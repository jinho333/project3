package com.commit.project3.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.commit.project3.mapper.AdminMapper;

import tools.jackson.databind.JsonNode;

// 예측 서버(ai_service)의 GET /routes 로 예측할 수 있는 노선번호 목록을 받아 DB의 노선 표(BUS_ROUTE)에 맞춰 넣음
// 없던 노선만 추가하고, 이미 있는 노선(이름 등)은 건드리지 않음. 모델을 다시 학습해 노선이 늘었을 때 다시 실행하면 됨
// 새로 들어가는 노선은 이름을 아직 모르므로 UNNAMED("이름 미등록")로 넣고, 노선 관리 화면에서 이름을 고치면 됨
// 예측 서버가 꺼져 있거나 DB 저장이 실패해도 예외를 던지지 않고 안내 문구로 알려 줌 (관리자 화면의 다른 부분은 그대로 동작)
@Service
public class ForecastRouteSyncService {

    // 한 번에 넣는 노선 수의 상한: 쿼리 하나가 너무 길어지지 않게 나눠서 넣음
    private static final int CHUNK_SIZE = 500;
    // 이름을 모르는 노선에 임시로 넣는 이름 (BUS_ROUTE.ROUTE_NAME 은 NOT NULL)
    public static final String UNNAMED = "이름 미등록";
    // 노선번호 형식: 사용자 접수 화면·노선 관리와 같은 규칙
    private static final String ROUTE_REGEX = "^[A-Za-z0-9가-힣\\-]{1,20}$";

    // ok: 예측 서버에서 목록을 받아 맞췄는지. total: 예측 서버가 알려 준 노선 수, added: 새로 넣은 수, skipped: 형식이 맞지 않아 뺀 수
    public record Result(boolean ok, String message, int total, int added, int skipped) {
        static Result failed(String message) {
            return new Result(false, message, 0, 0, 0);
        }
    }

    private final AdminMapper adminMapper;
    private final RestClient client;
    private final String apiKey;
    private final boolean baseUrlSet;

    public ForecastRouteSyncService(AdminMapper adminMapper,
                                    @Value("${py.base.url:}") String baseUrl,
                                    @Value("${py.api.key:}") String apiKey) {
        this.adminMapper = adminMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();

        // 예측 서버가 멈춰도 오래 기다리지 않게 시간 제한을 둠
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl.trim());
        }
        this.client = builder.build();
        this.baseUrlSet = baseUrl != null && !baseUrl.isBlank();
    }

    @Transactional
    public Result sync() {
        if (!baseUrlSet || apiKey.isEmpty()) {
            return Result.failed("예측 서버가 설정되지 않았습니다. application.yaml 의 py.base.url, py.api.key 를 확인하세요.");
        }

        JsonNode body;
        try {
            body = client.get().uri("/routes").header("X-API-Key", apiKey).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            System.err.println("[ForecastRoute] 호출 실패: HTTP " + code);
            return Result.failed(switch (code) {
                case 404 -> "예측 서버가 노선 목록 주소(/routes)를 모릅니다. 예측 서버가 옛 코드로 켜져 있다면 새 코드로 다시 시작하세요.";
                case 401 -> "예측 서버가 키를 받아들이지 않았습니다. py.api.key 와 예측 서버의 PREDICT_API_KEY 가 같은지 확인하세요.";
                case 503 -> "예측 서버에 저장된 모델이 없습니다. 모델 파일을 확인하세요.";
                default -> "예측 서버가 노선 목록을 주지 않았습니다(HTTP " + code + "). 서버 상태를 확인하세요.";
            });
        } catch (RestClientException e) {
            System.err.println("[ForecastRoute] 연결 실패: " + e.getClass().getSimpleName());
            return Result.failed("예측 서버에 연결하지 못했습니다. 서버가 켜져 있는지 확인하세요.");
        }

        if (body == null || !body.path("routes").isArray()) {
            return Result.failed("예측 서버의 응답 형식이 올바르지 않습니다.");
        }
        Set<String> routeSet = new LinkedHashSet<>();     // 중복은 한 번만
        int skipped = 0;
        for (JsonNode r : body.path("routes")) {
            String routeNo = r.asString("").trim();
            if (routeNo.matches(ROUTE_REGEX)) {
                routeSet.add(routeNo);
            } else {
                skipped++;
            }
        }
        if (routeSet.isEmpty()) {
            // 빈 목록을 "성공"으로 보지 않음: 표를 맞췄다고 착각하지 않게
            return Result.failed("예측 서버가 받을 수 있는 노선이 없다고 알려 주었습니다. 모델 파일을 확인하세요.");
        }

        List<String> routeList = new ArrayList<>(routeSet);
        try {
            int before = adminMapper.countRoute();
            for (int from = 0; from < routeList.size(); from += CHUNK_SIZE) {
                List<String> chunk = routeList.subList(from, Math.min(from + CHUNK_SIZE, routeList.size()));
                adminMapper.insertRouteIgnoreList(chunk, UNNAMED);
            }
            int added = adminMapper.countRoute() - before;
            return new Result(true, message(routeList.size(), added, skipped), routeList.size(), added, skipped);
        } catch (DataAccessException e) {
            System.err.println("[ForecastRoute] DB 저장 실패: " + e.getClass().getSimpleName());
            return Result.failed("노선 표(BUS_ROUTE)에 저장하지 못했습니다. DB 연결과 표 구조를 확인하세요.");
        }
    }

    private String message(int total, int added, int skipped) {
        String text = "예측 노선 " + total + "개를 확인했고 새로 " + added + "개를 추가했습니다. 새 노선의 이름은 \"" + UNNAMED + "\"으로 들어갑니다.";
        if (added == 0) {
            text = "예측 노선 " + total + "개를 확인했고 이미 모두 들어 있습니다.";
        }
        return skipped > 0 ? text + " (번호 형식이 맞지 않아 " + skipped + "개는 뺐습니다.)" : text;
    }
}
