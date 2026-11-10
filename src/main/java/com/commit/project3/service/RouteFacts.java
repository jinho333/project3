package com.commit.project3.service;

import com.commit.project3.dto.RouteOpenCountDTO;

import java.util.ArrayList;
import java.util.List;

// 노선 하나의 신고와 이용자 수 예측 (노선 분석과 질문이 같이 씀). stats 는 예측이 없으면 null
record RouteFacts(String routeNo, RouteOpenCountDTO open, Integer oldestDays,
                  RidershipForecastService.Ridership ridership, RouteFacts.Stats stats) {

    int total() { return open.getReceivedCnt() + open.getProcessingCnt(); }

    // 값이 같은 연속된 날의 묶음 (예: 11/9(월)~11/13(금) 하루 9,713명). 이 모델은 평일 값이 모두 같아서 "가장 많은 날"이 아니라 묶음으로 말해야 정확함
    record Run(RidershipForecastService.Day first, RidershipForecastService.Day last, int value) {}

    // 예측의 요약: 오늘부터 7일 하루 평균, 같은 값끼리 묶은 날짜 묶음(오늘부터 7일), 14일 중 가장 많은/적은 날(묶음이 많을 때 대신 씀)
    record Stats(int avg7, List<Run> runs, RidershipForecastService.Day max, RidershipForecastService.Day min, int dayCount) {}

    static Stats statsOf(RidershipForecastService.Ridership r) {
        if (!"OK".equals(r.status()) || r.days().isEmpty()) {
            return null;
        }
        List<RidershipForecastService.Day> days = r.days();
        int n = Math.min(RouteFactText.AVG_DAYS, days.size());
        long sum = 0;
        List<Run> runs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            RidershipForecastService.Day d = days.get(i);
            sum += d.predicted();
            Run lastRun = runs.isEmpty() ? null : runs.get(runs.size() - 1);
            if (lastRun != null && lastRun.value() == d.predicted()) {
                runs.set(runs.size() - 1, new Run(lastRun.first(), d, lastRun.value()));   // 같은 값이 이어지면 묶음을 늘림
            } else {
                runs.add(new Run(d, d, d.predicted()));
            }
        }
        RidershipForecastService.Day max = days.get(0), min = days.get(0);
        for (RidershipForecastService.Day d : days) {
            if (d.predicted() > max.predicted()) max = d;
            if (d.predicted() < min.predicted()) min = d;
        }
        return new Stats((int) Math.round((double) sum / n), runs, max, min, days.size());
    }
}
