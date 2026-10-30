package com.commit.project3.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Gemini API 호출만 맡는 클래스 (다른 AI 서비스로 바꿀 때 이 클래스만 고치면 됨)
@Component
public class GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final int MAX_OUTPUT_TOKENS = 4096;

    // 호출이 실패했을 때 화면에 그대로 보여 줄 수 있는 안내 문구를 담음
    public static class GeminiException extends RuntimeException {
        public GeminiException(String message) {
            super(message);
        }
    }

    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api.key:}") String apiKey,
                        @Value("${gemini.api.model:gemini-3.5-flash-lite}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;

        // 응답이 느려도 화면이 오래 멈추지 않게 시간 제한을 둠
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    // 지시문(systemText)과 질문(userText)을 보내고 답변 글만 돌려줌
    public String generate(String systemText, String userText) {
        return generate(systemText, userText, 0.3);
    }

    // temperature: 낮을수록 같은 입력에 항상 비슷한 답 (사실 정리용은 낮게)
    public String generate(String systemText, String userText, double temperature) {
        if (apiKey.isEmpty()) {
            throw new GeminiException("AI 비서가 설정되지 않았습니다. application.yaml 의 gemini.api.key 를 확인하세요.");
        }

        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))),
            "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userText)))),
            "generationConfig", Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS, "temperature", temperature)
        );

        JsonNode response;
        try {
            response = client.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)   // 키는 주소가 아니라 헤더로 보냄 (로그에 남지 않게)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            // 키·한도 문제 등 원인을 구분해 안내 (상세 내용은 서버 로그에만 남김)
            String detail = e.getMessage() == null ? "" : e.getMessage();
            System.err.println("[Gemini] 호출 실패: " + detail.replaceAll("key=[^&\\s]+", "key=***"));
            if (detail.contains("429")) {
                throw new GeminiException("AI 호출 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.");
            }
            throw new GeminiException("AI 서비스에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        String text = extractText(response);
        if (text.isBlank()) {
            throw new GeminiException("AI가 답변을 만들지 못했습니다. 질문을 바꿔 다시 시도해 주세요.");
        }
        return text;
    }

    // 응답의 candidates[0].content.parts[*].text 를 이어 붙임
    private String extractText(JsonNode response) {
        StringBuilder sb = new StringBuilder();
        if (response == null) {
            return "";
        }
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            sb.append(part.path("text").asString(""));
        }
        return sb.toString().trim();
    }
}
