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

// 노선 하나의 일별 이용자 수 예측: 파이썬 예측 서버(ai_service)의 GET /predict 를 불러서 화면용 모양으로 바꿈
// 값은 "하루 이용자 수(명)"이고 합성데이터로 학습한 통계적 경향
// 예측 서버가 꺼져 있거나 키가 없어도 예외를 던지지 않고 status 로 알려 줘서, 화면의 다른 부분은 그대로 동작함
@Service
public class RidershipForecastService {

    // 오늘부터 며칠을 보여 줄지
    public static final int DAYS = 14;

    // status: OK(days 에 값 있음) / UNKNOWN_ROUTE(모델이 모르는 노선) / OUT_OF_RANGE(예측 가능한 기간 밖) / UNAVAILABLE(예측 서버를 쓸 수 없음)
    // message 는 OK 가 아닐 때 화면에 그대로 보여 줄 안내 문구
    public record Day(String date, String dayOfWeek, int predicted) {}

    public record Ridership(String routeNo, String status, String message, List<Day> days,
                            String modelLabel, String trainedEnd, String predictUntil, String notice) {
        static Ridership failed(String routeNo, String status, String message) {
            return new Ridership(routeNo, status, message, List.of(), null, null, null, null);
        }
    }

    private final RestClient client;
    private final String apiKey;
    private final boolean baseUrlSet;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    public RidershipForecastService(@Value("${py.base.url:}") String baseUrl,
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

    public Ridership find(String routeNo) {
        return find(routeNo, LocalDate.now());
    }

    // from 부터 DAYS 일 (시험용으로 시작 날짜를 바꿔 부를 수 있음)
    public Ridership find(String routeNo, LocalDate from) {
        return find(routeNo, from, DAYS);
    }

    // 하루치만 (지도 노선의 혼잡 등급용): 그날 하루의 예상 이용자 수는 결과의 days 첫 값
    public Ridership findDay(String routeNo, LocalDate day) {
        return find(routeNo, day, 1);
    }

    // from 부터 days 일
    private Ridership find(String routeNo, LocalDate from, int days) {
        if (!baseUrlSet || apiKey.isEmpty()) {
            return Ridership.failed(routeNo, "UNAVAILABLE",
                "예측 서버가 설정되지 않았습니다. application.yaml 의 py.base.url, py.api.key 를 확인하세요.");
        }
        LocalDate to = from.plusDays(days - 1);
        try {
            JsonNode body = client.get()
                .uri(b -> b.path("/predict").queryParam("routeNo", routeNo)
                    .queryParam("from", from.toString()).queryParam("to", to.toString())
                    .queryParam("clamp", "true")           // 14일 창이 예측 가능한 마지막 날을 걸쳐도 앞쪽 날짜는 받음
                    .build())
                .header("X-API-Key", apiKey)            // 키는 주소가 아니라 헤더로 보냄
                .retrieve()
                .body(JsonNode.class);
            return parse(routeNo, body);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 404) {
                return Ridership.failed(routeNo, "UNKNOWN_ROUTE", routeNo + "번 노선은 예측 모델이 학습하지 않은 노선입니다.");
            }
            if (code == 422) {
                return Ridership.failed(routeNo, "OUT_OF_RANGE", detailOf(e.getResponseBodyAsString()));
            }
            System.err.println("[Predict] 호출 실패: HTTP " + code);   // 401(키 불일치), 503(모델 없음) 등
            return Ridership.failed(routeNo, "UNAVAILABLE", "예측 서버를 쓸 수 없습니다. 잠시 후 다시 시도하세요.");
        } catch (RestClientException e) {
            System.err.println("[Predict] 연결 실패: " + e.getClass().getSimpleName());
            return Ridership.failed(routeNo, "UNAVAILABLE", "예측 서버에 연결하지 못했습니다. 서버가 켜져 있는지 확인하세요.");
        }
    }

    private Ridership parse(String routeNo, JsonNode body) {
        if (body == null || !body.path("predictions").isArray()) {
            return Ridership.failed(routeNo, "UNAVAILABLE", "예측 서버의 응답 형식이 올바르지 않습니다.");
        }
        List<Day> days = new ArrayList<>();
        for (JsonNode p : body.path("predictions")) {
            days.add(new Day(p.path("date").asString(""), p.path("dayOfWeek").asString(""), p.path("predicted").asInt(0)));
        }
        JsonNode model = body.path("model");
        return new Ridership(routeNo, "OK", null, days, model.path("label").asString(""),
            model.path("trainedEnd").asString(""), model.path("predictUntil").asString(""), body.path("notice").asString(""));
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
