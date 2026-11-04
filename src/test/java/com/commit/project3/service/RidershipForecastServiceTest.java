package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

// 예측 서버(FastAPI)를 가짜 HTTP 서버로 흉내 내서, 응답 종류마다 화면용 결과가 맞게 나오는지 확인 (DB·실제 파이썬 서버 필요 없음)
class RidershipForecastServiceTest {

    private HttpServer server;
    private String lastApiKey;
    private String lastQuery;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // 모든 /predict 요청에 status, body 로 답하는 가짜 서버를 띄우고 주소를 돌려줌
    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", ex -> {
            lastApiKey = ex.getRequestHeaders().getFirst("X-API-Key");
            lastQuery = ex.getRequestURI().getRawQuery();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void okResponseIsConvertedAndKeyAndDatesAreSent() throws IOException {
        String url = serve(200, """
            {"routeNo":"134","model":{"label":"기준선","trainedEnd":"2026-10-08","predictUntil":"2026-12-03"},
             "predictions":[{"date":"2026-11-04","dayOfWeek":"수","predicted":1234},{"date":"2026-11-05","dayOfWeek":"목","predicted":1300}],
             "notice":"합성데이터 기반"}""");
        RidershipForecastService service = new RidershipForecastService(url, "secret-key-secret-key");

        RidershipForecastService.Ridership r = service.find("134", LocalDate.of(2026, 11, 4));

        assertEquals("OK", r.status());
        assertEquals(2, r.days().size());
        assertEquals(1234, r.days().get(0).predicted());
        assertEquals("secret-key-secret-key", lastApiKey);
        assertTrue(lastQuery.contains("routeNo=134") && lastQuery.contains("from=2026-11-04") && lastQuery.contains("to=2026-11-17")
            && lastQuery.contains("clamp=true"), lastQuery);
    }

    @Test
    void unknownRouteAndOutOfRangeAreExplainedWithoutError() throws IOException {
        String url = serve(404, "{\"detail\":\"없는 노선\"}");
        assertEquals("UNKNOWN_ROUTE", new RidershipForecastService(url, "k").find("999").status());
        server.stop(0);

        url = serve(422, "{\"detail\":\"날짜는 2024-12-21 ~ 2026-12-03 사이여야 합니다\"}");
        RidershipForecastService.Ridership r = new RidershipForecastService(url, "k").find("134");
        assertEquals("OUT_OF_RANGE", r.status());
        assertTrue(r.message().contains("2026-12-03"));
    }

    @Test
    void serverErrorsAndMissingSettingsAreUnavailable() throws IOException {
        String url = serve(401, "{\"detail\":\"키 불일치\"}");
        assertEquals("UNAVAILABLE", new RidershipForecastService(url, "k").find("134").status());

        assertEquals("UNAVAILABLE", new RidershipForecastService("", "k").find("134").status());       // 주소 없음
        assertEquals("UNAVAILABLE", new RidershipForecastService(url, "").find("134").status());       // 키 없음
        server.stop(0);
        assertEquals("UNAVAILABLE", new RidershipForecastService(url, "k").find("134").status());      // 서버 꺼짐
    }
}
