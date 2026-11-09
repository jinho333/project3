package com.commit.project3.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.commit.project3.dto.RouteDTO;
import com.commit.project3.mapper.AdminMapper;

import tools.jackson.databind.json.JsonMapper;

// 지도에 보여 줄 노선 목록(JSON)과, 신고 처리 우선순위가 쓰는 노선의 임시 혼잡 등급을 만들어 주는 서비스
//  - 노선 목록: 지도에 그릴 수 있는 노선(노선 선 파일이 있는 노선)의 번호와, DB(BUS_ROUTE)에 있는 노선 이름. 노선번호순
//    DB 에 이름이 없는 노선도 목록에는 나오고 이름은 "이름 미등록"으로 보임. DB 를 읽지 못해도 목록은 나옴
//  - 혼잡 등급: 목록의 노선들이 오늘 예상하는 이용자 수(예측 서버 /predict)를 1위 100%로 한 비율로 바꿔 혼잡 등급 경계(80%, 50%)에 댐
//    예측 서버가 꺼져 있거나 값을 못 받은 노선은 등급이 없음("예측 없음")이고, 다른 화면이 멈추지 않게 예외를 던지지 않음
//    요청마다 예측 서버를 부르지 않도록 결과를 잠깐(10분) 기억하고, 못 받았을 때는 짧게(30초) 기억해 다시 시도함
@Service
public class RouteListService {

    private static final Logger log = LoggerFactory.getLogger(RouteListService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final long FAILED_CACHE_MS = 30 * 1000L;
    static final String UNNAMED = "이름 미등록";     // BUS_ROUTE 에 이름이 없는 노선

    private final AdminMapper adminMapper;
    private final RouteGeometryService routeGeometryService;
    private final RidershipForecastService ridershipForecastService;
    private final Supplier<LocalDate> today;
    private final LongSupplier nowMillis;

    private Map<String, String> cachedGrades;       // 노선번호 -> 등급(HIGH / MID / LOW), 값을 못 받은 노선은 빠짐
    private long cachedAt;
    private long cacheMs;

    @Autowired
    public RouteListService(AdminMapper adminMapper, RouteGeometryService routeGeometryService,
                            RidershipForecastService ridershipForecastService) {
        this(adminMapper, routeGeometryService, ridershipForecastService, LocalDate::now, System::currentTimeMillis);
    }

    // 시험용: 오늘 날짜와 현재 시각을 바꿔 끼울 수 있음
    RouteListService(AdminMapper adminMapper, RouteGeometryService routeGeometryService,
                     RidershipForecastService ridershipForecastService, Supplier<LocalDate> today, LongSupplier nowMillis) {
        this.adminMapper = adminMapper;
        this.routeGeometryService = routeGeometryService;
        this.ridershipForecastService = ridershipForecastService;
        this.today = today;
        this.nowMillis = nowMillis;
    }

    // 지도 화면이 받는 JSON: { routes: [{ routeNo, label }] } (노선번호순)
    public String findRouteListJson() {
        List<String> routeNos = new ArrayList<>(routeGeometryService.findRouteNos());
        routeNos.sort(RouteListService::compareRouteNo);

        Map<String, String> names = new HashMap<>();
        if (!routeNos.isEmpty()) {
            try {
                for (RouteDTO r : adminMapper.selectRouteNames(routeNos)) {
                    names.put(r.getRouteNo(), r.getRouteName());
                }
            } catch (DataAccessException e) {
                log.warn("노선 이름을 DB 에서 읽지 못했습니다: {}", e.getClass().getSimpleName());
            }
        }
        return toJson(routeNos, names);
    }

    // 노선의 임시 혼잡 등급(HIGH / MID / LOW): 지도 노선이 아니거나 오늘 이용자 수를 못 받았으면 비어 있는 Optional (= 예측 없음)
    public Optional<String> findGrade(String routeNo) {
        return Optional.ofNullable(grades().get(routeNo));
    }

    private synchronized Map<String, String> grades() {
        long now = nowMillis.getAsLong();
        if (cachedGrades != null && now - cachedAt < cacheMs) {
            return cachedGrades;
        }
        Map<String, Integer> riders = collectRiders();
        cachedGrades = gradesOf(riders);
        cachedAt = now;
        cacheMs = riders.isEmpty() ? FAILED_CACHE_MS : CACHE_MS;
        return cachedGrades;
    }

    // 지도에 그릴 수 있는 노선마다 오늘 하루 예상 이용자 수를 예측 서버에서 받음 (못 받은 노선은 빠짐)
    private Map<String, Integer> collectRiders() {
        Map<String, Integer> riders = new LinkedHashMap<>();
        LocalDate day = today.get();
        for (String routeNo : routeGeometryService.findRouteNos()) {
            RidershipForecastService.Ridership r = ridershipForecastService.findDay(routeNo, day);
            if ("OK".equals(r.status()) && !r.days().isEmpty()) {
                riders.put(routeNo, r.days().get(0).predicted());
            } else if ("UNAVAILABLE".equals(r.status())) {
                break;       // 서버가 안 되면 나머지 노선은 부르지 않음 (노선마다 기다리지 않도록)
            }
        }
        return riders;
    }

    // 이용자 수 -> 등급: 1위를 100%로 한 비율을 혼잡 등급 경계(RouteForecastService.gradeOf)에 댐
    static Map<String, String> gradesOf(Map<String, Integer> riders) {
        int max = riders.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<String, String> grades = new HashMap<>();
        if (max <= 0) {
            return grades;
        }
        riders.forEach((routeNo, count) ->
            grades.put(routeNo, RouteForecastService.gradeOf(Math.round(count * 100.0 / max))));
        return grades;
    }

    static String toJson(List<String> routeNos, Map<String, String> names) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String routeNo : routeNos) {
            String name = names.get(routeNo);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routeNo", routeNo);
            m.put("label", name == null || name.isBlank() ? UNNAMED : name);
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("routes", list);
        return JSON.writeValueAsString(root);
    }

    // 노선번호순: 숫자만 있는 번호는 숫자 크기로(9 < 10), 그 밖에는 글자순
    static int compareRouteNo(String a, String b) {
        boolean numeric = a.matches("\\d+") && b.matches("\\d+");
        if (numeric && a.length() != b.length()) {
            return Integer.compare(a.length(), b.length());
        }
        return Comparator.<String>naturalOrder().compare(a, b);
    }
}
