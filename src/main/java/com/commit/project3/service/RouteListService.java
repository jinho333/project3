package com.commit.project3.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
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
//  - 노선 목록: DB(BUS_ROUTE)에 등록된 노선 중 지도에 그릴 수 있는 노선(노선 선 파일이 있는 노선)의 번호와 DB 이름. 노선번호순
//    DB 에 없는 노선은 선 파일이 있어도 목록에 나오지 않고(DB 가 노선의 기준), 선 파일이 없는 DB 노선은 지도에 그릴 수 없어 빠짐.
//    DB 를 읽지 못하면 목록이 비지 않도록 선 파일이 있는 노선을 이름 없이("이름 미등록") 보여 줌
//  - 이용자 규모(%): 목록의 노선들이 오늘 예상하는 이용자 수(예측 서버 /predict)를 1위 노선 = 100%로 한 비율. 신고 처리 우선순위가 이 값을 씀
//    혼잡 등급은 이 비율을 등급 경계(80%, 50%)에 댄 것
//    예측 서버가 꺼져 있거나 값을 못 받은 노선은 등급이 없음("예측 없음")이고, 다른 화면이 멈추지 않게 예외를 던지지 않음
//    요청마다 예측 서버를 부르지 않도록 결과를 잠깐(10분) 기억하고, 못 받았을 때는 짧게(30초) 기억해 다시 시도함
@Service
public class RouteListService {

    private static final Logger log = LoggerFactory.getLogger(RouteListService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final long FAILED_CACHE_MS = 30 * 1000L;

    // 이용자 규모 등급 경계 (1위 노선 대비 %): 데이터가 모이면 실제 기준으로 바꿈
    static final double GRADE_HIGH_MIN = 80;
    static final double GRADE_MID_MIN = 50;
    private static final double NO_DATA_SCALE = 50;     // 값을 하나도 못 받았을 때 쓰는 중간값
    static final String UNNAMED = "이름 미등록";     // DB 를 읽지 못했거나 이름이 비어 있는 노선

    private final AdminMapper adminMapper;
    private final RouteGeometryService routeGeometryService;
    private final RidershipForecastService ridershipForecastService;
    private final Supplier<LocalDate> today;
    private final LongSupplier nowMillis;

    private Map<String, String> cachedGrades;       // 노선번호 -> 등급(HIGH / MID / LOW), 값을 못 받은 노선은 빠짐
    private Map<String, Double> cachedScales;       // 노선번호 -> 이용자 규모(1위 노선 대비 %, 0~100), 값을 못 받은 노선은 빠짐
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
        Map<String, String> routes = mapRoutes();
        return toJson(new ArrayList<>(routes.keySet()), routes);
    }

    // 지도에 보여 줄 노선: DB(BUS_ROUTE)에 등록되어 있고 노선 선 파일도 있는 노선 (번호 -> DB 이름, 노선번호순)
    // DB 를 읽지 못하면(DataAccessException) 선 파일이 있는 모든 노선을 이름 없이(null) 돌려줘 화면이 비지 않게 함
    private Map<String, String> mapRoutes() {
        List<String> routeNos = new ArrayList<>(routeGeometryService.findRouteNos());
        routeNos.sort(RouteListService::compareRouteNo);

        Map<String, String> routes = new LinkedHashMap<>();
        if (routeNos.isEmpty()) {
            return routes;
        }
        try {
            Map<String, String> names = new HashMap<>();
            for (RouteDTO r : adminMapper.selectRouteNames(routeNos)) {
                names.put(r.getRouteNo(), r.getRouteName());
            }
            for (String routeNo : routeNos) {
                if (names.containsKey(routeNo)) {
                    routes.put(routeNo, names.get(routeNo));
                }
            }
        } catch (DataAccessException e) {
            log.warn("노선 목록을 DB 에서 읽지 못해 선 파일이 있는 노선을 이름 없이 보여 줍니다: {}", e.getClass().getSimpleName());
            routes.clear();
            for (String routeNo : routeNos) {
                routes.put(routeNo, null);
            }
        }
        return routes;
    }

    // 노선의 혼잡 등급(HIGH / MID / LOW): 지도 노선이 아니거나 오늘 이용자 수를 못 받았으면 비어 있는 Optional (= 예측 없음)
    public synchronized Optional<String> findGrade(String routeNo) {
        load();
        return Optional.ofNullable(cachedGrades.get(routeNo));
    }

    // 노선의 이용자 규모(%): 오늘 예상 이용자 수 ÷ 모든 노선 중 가장 큰 이용자 수 × 100. 값을 못 받은 노선은 비어 있음
    public synchronized OptionalDouble findScale(String routeNo) {
        load();
        Double scale = cachedScales.get(routeNo);
        return scale == null ? OptionalDouble.empty() : OptionalDouble.of(scale);
    }

    // 예측이 없는 노선에 쓸 이용자 규모: 모든 노선 규모의 중간값 (모르는 것을 낮게도 높게도 보지 않음)
    // 이용자 수는 소수 노선에 몰려 있어서 중간값이 50보다 훨씬 낮으므로, 고정 50을 쓰면 예측 없는 노선이 절반 이상의 노선보다 앞서게 됨
    public synchronized double neutralScale() {
        load();
        return medianOf(cachedScales.values(), NO_DATA_SCALE);
    }

    // 캐시가 오래됐으면 예측 서버에서 다시 받아 등급과 규모를 같이 갱신
    private void load() {
        long now = nowMillis.getAsLong();
        if (cachedGrades != null && now - cachedAt < cacheMs) {
            return;
        }
        Map<String, Integer> riders = collectRiders();
        cachedGrades = gradesOf(riders);
        cachedScales = scalesOf(riders);
        cachedAt = now;
        cacheMs = riders.isEmpty() ? FAILED_CACHE_MS : CACHE_MS;
    }

    // 지도에 그릴 수 있는 노선의 오늘 하루 예상 이용자 수를 예측 서버에서 한 번에 받음 (못 받은 노선은 빠짐)
    // 노선마다 부르면 노선이 200개 가까이라 신고 관리·지도 목록을 처음 열 때 오래 걸려서, 하루치 전체를 주는 /predict/day 를 한 번만 부름
    private Map<String, Integer> collectRiders() {
        Set<String> mapRouteNos = new HashSet<>(routeGeometryService.findRouteNos());
        Map<String, Integer> riders = new LinkedHashMap<>();
        ridershipForecastService.findDayRiders(today.get()).forEach((routeNo, count) -> {
            if (mapRouteNos.contains(routeNo)) {
                riders.put(routeNo, count);
            }
        });
        return riders;
    }

    // 이용자 수 -> 이용자 규모(%): 가장 큰 노선을 100으로 한 비율
    static Map<String, Double> scalesOf(Map<String, Integer> riders) {
        int max = riders.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<String, Double> scales = new HashMap<>();
        if (max <= 0) {
            return scales;
        }
        riders.forEach((routeNo, count) -> scales.put(routeNo, count * 100.0 / max));
        return scales;
    }

    // 이용자 규모 -> 등급: 이용자 수를 1위 100%로 한 비율을 등급 경계에 댐
    static Map<String, String> gradesOf(Map<String, Integer> riders) {
        Map<String, String> grades = new HashMap<>();
        // 예전 계산과 같도록 비율을 먼저 반올림한 뒤 등급 경계에 댐
        scalesOf(riders).forEach((routeNo, scale) -> grades.put(routeNo, gradeOf(Math.round(scale))));
        return grades;
    }

    static String gradeOf(double scale) {
        if (scale >= GRADE_HIGH_MIN) return "HIGH";
        return scale >= GRADE_MID_MIN ? "MID" : "LOW";
    }

    static double medianOf(Collection<Double> values, double ifEmpty) {
        if (values.isEmpty()) {
            return ifEmpty;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
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
