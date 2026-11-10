package com.commit.project3.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 오늘 예상 이용자 수가 많은 노선 순위 (신고 접수의 노선 선택 창을 이용자 수 순으로 정렬할 때 씀)
//  - 순위: 예측 서버의 /predict/day 를 한 번 불러 모든 노선을 많은 순으로 받음 (노선마다 부르지 않음)
//  - 결과를 10분 기억하고(날짜가 바뀌면 다시 받음), 못 받았을 때는 30초만 기억해 금방 다시 시도함
//  - 예측 서버가 꺼져 있거나 날짜가 예측 가능한 기간 밖이면 예외 없이 빈 순위: 호출한 쪽은 원래 순서(노선번호순)를 그대로 씀
@Service
public class RouteRankService {

    private static final long CACHE_MS = 10 * 60 * 1000L;
    private static final long FAILED_CACHE_MS = 30 * 1000L;

    private final RidershipForecastService ridershipForecastService;
    private final Supplier<LocalDate> today;
    private final LongSupplier nowMillis;

    private Map<String, Integer> cached;        // 노선번호 -> 0부터 시작하는 순위 (0이 이용자 수 1위)
    private LocalDate cachedDay;
    private long cachedAt;
    private long cacheMs;

    @Autowired
    public RouteRankService(RidershipForecastService ridershipForecastService) {
        this(ridershipForecastService, LocalDate::now, System::currentTimeMillis);
    }

    // 시험용: 오늘 날짜와 현재 시각을 바꿔 끼울 수 있음
    RouteRankService(RidershipForecastService ridershipForecastService, Supplier<LocalDate> today, LongSupplier nowMillis) {
        this.ridershipForecastService = ridershipForecastService;
        this.today = today;
        this.nowMillis = nowMillis;
    }

    // 노선번호 -> 순위(0이 1위). 순위를 못 받았으면 빈 지도
    public synchronized Map<String, Integer> ranks() {
        LocalDate day = today.get();
        long now = nowMillis.getAsLong();
        if (cached != null && day.equals(cachedDay) && now - cachedAt < cacheMs) {
            return cached;
        }
        List<String> ordered = ridershipForecastService.findDayRanking(day);
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            ranks.putIfAbsent(ordered.get(i), i);
        }
        cached = ranks;
        cachedDay = day;
        cachedAt = now;
        cacheMs = ranks.isEmpty() ? FAILED_CACHE_MS : CACHE_MS;
        return cached;
    }
}
