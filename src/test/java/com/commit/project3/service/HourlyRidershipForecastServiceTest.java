package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

// 예측 서버(FastAPI)를 가짜 HTTP 서버로 흉내 내서, 응답 종류마다 화면용 결과가 맞게 나오는지 확인 (DB·실제 파이썬 서버 필요 없음)
class HourlyRidershipForecastServiceTest {

    private HttpServer server;
    private String lastApiKey;
    private String lastQuery;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict/hourly", ex -> {
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

    private static String hoursJson(int count) {
        return IntStream.range(0, count).mapToObj(h -> "{\"hour\":" + h + ",\"predicted\":" + (h * 10) + "}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void okResponseIsConvertedAndKeyAndDateAreSent() throws IOException {
        String url = serve(200, "{\"routeNo\":\"134\",\"date\":\"2026-11-06\",\"dayOfWeek\":\"금\",\"dailyPredicted\":1234,\"hours\":" + hoursJson(24)
            + ",\"shareBasis\":\"blend\",\"shareDayKind\":0,\"limited\":true,\"provisional\":true,\"model\":{\"label\":\"기준선\"},\"notice\":\"합성데이터 기반\"}");
        HourlyRidershipForecastService service = new HourlyRidershipForecastService(url, "secret-key-secret-key");

        HourlyRidershipForecastService.HourlyRidership r = service.find("134", LocalDate.of(2026, 11, 6));

        assertEquals("OK", r.status());
        assertEquals(24, r.hours().size());
        assertEquals(230, r.hours().get(23).predicted());
        assertEquals(1234, r.dailyPredicted());
        assertTrue(r.provisional());
        assertTrue(r.limited());
        assertEquals(0, r.shareDayKind());
        assertEquals("blend", r.shareBasis());
        assertEquals("secret-key-secret-key", lastApiKey);
        assertTrue(lastQuery.contains("routeNo=134") && lastQuery.contains("date=2026-11-06"), lastQuery);
    }

    @Test
    void wrongHourCountIsTreatedAsBadResponse() throws IOException {
        String url = serve(200, "{\"hours\":" + hoursJson(5) + "}");
        assertEquals("UNAVAILABLE", new HourlyRidershipForecastService(url, "k").find("134", LocalDate.of(2026, 11, 6)).status());
    }

    @Test
    void unknownRouteAndOutOfRangeAreExplainedWithoutError() throws IOException {
        LocalDate day = LocalDate.of(2026, 11, 6);
        String url = serve(404, "{\"detail\":\"없는 노선\"}");
        assertEquals("UNKNOWN_ROUTE", new HourlyRidershipForecastService(url, "k").find("999", day).status());
        server.stop(0);

        url = serve(422, "{\"detail\":\"날짜는 2024-12-21 ~ 2026-12-03 사이여야 합니다\"}");
        HourlyRidershipForecastService.HourlyRidership r = new HourlyRidershipForecastService(url, "k").find("134", day);
        assertEquals("OUT_OF_RANGE", r.status());
        assertTrue(r.message().contains("2026-12-03"));
    }

    @Test
    void serverErrorsAndMissingSettingsAreUnavailable() throws IOException {
        LocalDate day = LocalDate.of(2026, 11, 6);
        String url = serve(503, "{\"detail\":\"시간대 비율표가 없습니다\"}");
        assertEquals("UNAVAILABLE", new HourlyRidershipForecastService(url, "k").find("134", day).status());

        assertEquals("UNAVAILABLE", new HourlyRidershipForecastService("", "k").find("134", day).status());   // 주소 없음
        assertEquals("UNAVAILABLE", new HourlyRidershipForecastService(url, "").find("134", day).status());   // 키 없음
        server.stop(0);
        assertEquals("UNAVAILABLE", new HourlyRidershipForecastService(url, "k").find("134", day).status());  // 서버 꺼짐
    }

    @Test
    void selectableDatesAreTodayThroughSixDaysAheadWithOneDayToleranceEachSide() {
        LocalDate today = LocalDate.of(2026, 11, 5);
        assertTrue(HourlyRidershipForecastService.isSelectable(today, today));
        assertTrue(HourlyRidershipForecastService.isSelectable(today.plusDays(6), today));
        assertTrue(HourlyRidershipForecastService.isSelectable(today.plusDays(7), today));      // 브라우저 시계가 하루 앞선 경우
        assertTrue(HourlyRidershipForecastService.isSelectable(today.minusDays(1), today));     // 자정을 넘겨 열어 둔 화면
        assertFalse(HourlyRidershipForecastService.isSelectable(today.plusDays(8), today));
        assertFalse(HourlyRidershipForecastService.isSelectable(today.minusDays(2), today));
        assertFalse(HourlyRidershipForecastService.isSelectable(null, today));
    }
}
