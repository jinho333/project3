package com.commit.project3.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 노선 하나의 혼잡도 예측: 운행 시간대별 "평소 평균 혼잡도"(%) 곡선과, 접속 시각 1시간 뒤의 예측값
// 지금은 임시 값(temp = true)을 만들어 주고, 예측 모델이 준비되면 임시 값을 만드는 부분(tempBaseline, tempForecast)만 모델 결과로 바꾸면 됨
// 평소 평균과 예측값의 차이는 평소 값 대비 비율(%)로 보고, 10% 이상 벗어나면 alert (높든 낮든 평소와 크게 다른 상황)
@Service
public class RouteForecastService {

    // 임시 운행 시간: 5시부터 23시까지 (시간대 5~22)
    public static final int OPEN_HOUR = 5;
    public static final int CLOSE_HOUR = 23;

    // 평소보다 이 비율(%) 이상 벗어나면 강조
    public static final double DEVIATION_ALERT_PCT = 10;
    // 평소 혼잡도가 이 값(%) 미만이면 조금만 달라도 비율이 크게 나와서 강조하지 않음 (0 이면 끔)
    public static final double DEVIATION_MIN_BASELINE = 10;

    // 임시 혼잡 등급 경계 (예측값 %): 데이터가 모이면 실제 기준으로 바꿈
    public static final double GRADE_HIGH_MIN = 80;
    public static final double GRADE_MID_MIN = 50;

    private static final String LIST_PATH = "static/data/route-list.json";

    // 예측 결과: status 가 OK 이면 targetHour 이하 값이 채워지고, OUTSIDE_HOURS 이면 평소 곡선만 있음
    // deviationPct: (예측 - 평소) / 평소 × 100, 양수면 평소보다 혼잡, 음수면 평소보다 한산
    // direction: UP / DOWN / SAME,  grade: HIGH / MID / LOW (예측값 기준)
    // gradeHighMin, gradeMidMin: 혼잡 등급 경계(%), 차트의 등급 띠를 서버 기준과 똑같이 그리기 위해 같이 보냄
    public record Forecast(String routeNo, boolean temp, int nowHour, List<Integer> hours, List<Integer> baseline,
                           String status, Integer targetHour, Double baselineAtTarget, Double forecast,
                           Double deviationPct, String direction, boolean alert, String grade, String message,
                           double gradeHighMin, double gradeMidMin) {}

    private final RouteListService routeListService;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RouteForecastService(RouteListService routeListService) {
        this.routeListService = routeListService;
    }

    // 노선 목록에 없는 노선이면 비어 있는 Optional, nowHour 는 지금 시각(0~23) (시험용으로 바꿔 부를 수 있음)
    public Optional<Forecast> find(String routeNo, int nowHour) {
        String grade = findListGrade(routeNo);
        if (grade == null) {
            return Optional.empty();
        }

        List<Integer> hours = new ArrayList<>();
        List<Integer> baseline = new ArrayList<>();
        for (int h = OPEN_HOUR; h < CLOSE_HOUR; h++) {
            hours.add(h);
            baseline.add(tempBaseline(grade, h));
        }

        int target = nowHour + 1;
        if (target < OPEN_HOUR || target >= CLOSE_HOUR) {
            return Optional.of(new Forecast(routeNo, true, nowHour, hours, baseline, "OUTSIDE_HOURS",
                target % 24, null, null, null, null, false, null, "운행 시간 외라 예측이 없습니다.",
                GRADE_HIGH_MIN, GRADE_MID_MIN));
        }

        double base = baseline.get(target - OPEN_HOUR);
        double predicted = tempForecast(routeNo, target, base);
        double deviation = Math.round((predicted - base) / base * 1000) / 10.0;     // 소수 첫째 자리까지
        boolean alert = base >= DEVIATION_MIN_BASELINE && Math.abs(deviation) >= DEVIATION_ALERT_PCT;
        String direction = deviation > 0 ? "UP" : (deviation < 0 ? "DOWN" : "SAME");
        return Optional.of(new Forecast(routeNo, true, nowHour, hours, baseline, "OK",
            target, base, predicted, deviation, direction, alert, gradeOf(predicted), null,
            GRADE_HIGH_MIN, GRADE_MID_MIN));
    }

    public Optional<Forecast> find(String routeNo) {
        return find(routeNo, LocalTime.now().getHour());
    }

    // 지금(nowHour)부터 hours 시간 동안의 예측 혼잡도(%) 평균: 신고 처리 우선순위의 이용자규모에 씀
    // 운행 시간 안의 시간대만 평균내고, 운행이 끝났거나 시작 전이면 다음 운행 시작(OPEN_HOUR)부터 hours 시간을 봄
    // 노선 목록에 없는 노선이면 비어 있는 값 (예측이 없다는 뜻)
    public OptionalDouble averageAhead(String routeNo, int nowHour, int hours) {
        String grade = findListGrade(routeNo);
        if (grade == null) {
            return OptionalDouble.empty();
        }
        int start = (nowHour < OPEN_HOUR || nowHour >= CLOSE_HOUR) ? OPEN_HOUR : nowHour;
        int end = Math.min(start + hours, CLOSE_HOUR);
        double sum = 0;
        for (int h = start; h < end; h++) {
            sum += tempForecast(routeNo, h, tempBaseline(grade, h));
        }
        return OptionalDouble.of(sum / (end - start));
    }

    public static String gradeOf(double congestion) {
        if (congestion >= GRADE_HIGH_MIN) return "HIGH";
        return congestion >= GRADE_MID_MIN ? "MID" : "LOW";
    }

    // ---- 임시 값 (모델이 준비되면 이 아래를 모델 결과로 교체) ----

    // 시간대별 평소 평균: 아침(8시)과 저녁(18시)에 두 번 붐비는 모양, 노선의 임시 혼잡 등급에 따라 높낮이가 다름
    private int tempBaseline(String grade, int hour) {
        double peak = "HIGH".equals(grade) ? 90 : ("MID".equals(grade) ? 70 : 45);
        double floor = 12;
        double morning = Math.exp(-Math.pow(hour - 8, 2) / (2 * 1.5 * 1.5));
        double evening = 0.9 * Math.exp(-Math.pow(hour - 18, 2) / (2 * 2.0 * 2.0));
        return (int) Math.round(floor + (peak - floor) * Math.max(morning, evening));
    }

    // 예측값: 평소 값에 노선·시각마다 정해진(같은 입력이면 같은) 변동을 줌, 대부분 몇 % 안이고 가끔 10% 넘게 벗어남
    private double tempForecast(String routeNo, int hour, double base) {
        long seed = (routeNo.hashCode() * 31L + hour) * 2654435761L;
        double noise = ((seed >>> 8) % 2001) / 1000.0 - 1;      // -1 ~ 1
        double factor = 1 + 0.2 * noise * noise * noise;        // 가운데 값이 많고, 끝으로 갈수록 드묾
        return Math.max(1, Math.min(100, Math.round(base * factor * 10) / 10.0));
    }

    // 노선 목록(임시 혼잡 등급)에서 이 노선의 등급을 찾음, 목록에 없으면 null
    private String findListGrade(String routeNo) {
        JsonNode root = jsonMapper.readTree(routeListService.findRouteListJson());
        for (JsonNode r : root.path("routes")) {
            if (routeNo.equals(r.path("routeNo").asString(""))) {
                return r.path("grade").asString("MID");
            }
        }
        return null;
    }
}
