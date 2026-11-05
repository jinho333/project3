package com.commit.project3.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 노선 하나·날짜 하나의 시간대별(0~23시) 예상 승차 인원: 파이썬 예측 서버(ai_service)의 GET /predict/hourly 를 불러서 화면용 모양으로 바꿈
// 일별 예측(RidershipForecastService)과 같은 방식: 서버가 꺼져 있거나 키가 없어도 예외 대신 status 로 알려 줘서 화면의 다른 부분은 그대로 동작함
// 값은 합성데이터로 학습한 통계적 경향이며, provisional 이 true 면 시간대 비율 자료가 적은 임시값, limited 가 true 면 이 날의 비율 근거가 약함(화면이 경고)
@Service
public class HourlyRidershipForecastService {

    // 오늘부터 며칠 안의 날짜를 고를 수 있는지 (오늘 포함)
    public static final int DAYS = 7;

    // status: OK(hours 에 24개) / UNKNOWN_ROUTE / OUT_OF_RANGE(예측 가능한 기간 밖) / UNAVAILABLE(예측 서버를 쓸 수 없음)
    public record Hour(int hour, int predicted) {}

    public record HourlyRidership(String routeNo, String date, String dayOfWeek, String status, String message,
                                  int dailyPredicted, List<Hour> hours, String shareBasis, int shareDayKind, boolean limited,
                                  boolean provisional, String modelLabel, String notice) {
        static HourlyRidership failed(String routeNo, String date, String status, String message) {
            return new HourlyRidership(routeNo, date, null, status, message, 0, List.of(), null, -1, false, false, null, null);
        }
    }

    private final RestClient client;
    private final String apiKey;
    private final boolean baseUrlSet;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public HourlyRidershipForecastService(@Value("${py.base.url:}") String baseUrl,
                                          @Value("${py.api.key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        // 예측 서버가 멈춰도 화면이 오래 기다리지 않게 시간 제한을 둠
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(3));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.trim());
        }
        this.client = builder.build();
        this.baseUrlSet = baseUrl != null && !baseUrl.isBlank();
    }

    // 오늘부터 DAYS 일 안의 날짜인지 (today 를 넣어 시험할 수 있음)
    // 화면은 브라우저 시계로 날짜 버튼을 만들고 여기서는 서버 시계로 검사하므로, 시간대·자정 경계 차이를 하루씩 눈감아 줌
    // (예측 가능 기간 밖은 어차피 예측 서버가 거절함)
    public static boolean isSelectable(LocalDate date, LocalDate today) {
        return date != null && !date.isBefore(today.minusDays(1)) && date.isBefore(today.plusDays(DAYS + 1));
    }

    public HourlyRidership find(String routeNo, LocalDate date) {
        String day = date.toString();
        if (!baseUrlSet || apiKey.isEmpty()) {
            return HourlyRidership.failed(routeNo, day, "UNAVAILABLE",
                "예측 서버가 설정되지 않았습니다. application.yaml 의 py.base.url, py.api.key 를 확인하세요.");
        }
        try {
            JsonNode body = client.get()
                .uri(b -> b.path("/predict/hourly").queryParam("routeNo", routeNo).queryParam("date", day).build())
                .header("X-API-Key", apiKey)            // 키는 주소가 아니라 헤더로 보냄
                .retrieve()
                .body(JsonNode.class);
            return parse(routeNo, day, body);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404) {
                return HourlyRidership.failed(routeNo, day, "UNKNOWN_ROUTE", routeNo + "번 노선은 예측 모델이 학습하지 않은 노선입니다.");
            }
            if (code == 422) {
                return HourlyRidership.failed(routeNo, day, "OUT_OF_RANGE", detailOf(e.getResponseBodyAsString()));
            }
            System.err.println("[PredictHourly] 호출 실패: HTTP " + code);   // 401(키 불일치), 503(모델·비율표 없음) 등
            return HourlyRidership.failed(routeNo, day, "UNAVAILABLE", "시간대별 예측을 쓸 수 없습니다. 잠시 후 다시 시도하세요.");
        } catch (RestClientException e) {
            System.err.println("[PredictHourly] 연결 실패: " + e.getClass().getSimpleName());
            return HourlyRidership.failed(routeNo, day, "UNAVAILABLE", "예측 서버에 연결하지 못했습니다. 서버가 켜져 있는지 확인하세요.");
        }
    }

    private HourlyRidership parse(String routeNo, String day, JsonNode body) {
        if (body == null || !body.path("hours").isArray() || body.path("hours").size() != 24) {
            return HourlyRidership.failed(routeNo, day, "UNAVAILABLE", "예측 서버의 응답 형식이 올바르지 않습니다.");
        }
        List<Hour> hours = new ArrayList<>();
        for (JsonNode h : body.path("hours")) {
            hours.add(new Hour(h.path("hour").asInt(0), h.path("predicted").asInt(0)));
        }
        return new HourlyRidership(routeNo, body.path("date").asString(day), body.path("dayOfWeek").asString(""), "OK", null,
            body.path("dailyPredicted").asInt(0), hours, body.path("shareBasis").asString(""), body.path("shareDayKind").asInt(-1), body.path("limited").asBoolean(false), body.path("provisional").asBoolean(false),
            body.path("model").path("label").asString(""), body.path("notice").asString(""));
    }

    // FastAPI 오류 본문 {"detail": "..."} 에서 안내 문구만 뽑음 (형식이 다르면 기본 문구)
    private String detailOf(String responseBody) {
        try {
            JsonNode detail = jsonMapper.readTree(responseBody).path("detail");
            if (detail.isString() && !detail.asString().isBlank()) {
                return detail.asString();
            }
        } catch (RuntimeException ignored) {
            // 아래 기본 문구
        }
        return "예측할 수 있는 기간이 아닙니다.";
    }
}
