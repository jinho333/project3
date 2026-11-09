package com.commit.project3.controller;

import com.commit.project3.service.AiService;
import com.commit.project3.service.GeminiClient;
import com.commit.project3.util.AdminUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 관리자 AI 비서 JSON 주소 (/admin/api/ai/...), 관리자만 호출 가능
// 성공: { "answer": "..." } / 실패: { "message": "..." }
@RestController
@RequestMapping("/admin/api/ai")
public class AdminAiController {

    private static final String AI_TIME_KEY = "adminAiAt";          // 마지막 관리자 AI 호출 시각 (세션)
    // 호출 사이 최소 간격: 시민용 AI(5초)와 Gemini 무료 한도(분당 15회)를 같이 쓰므로 한 세션이 분당 12회를 넘지 못하게 함
    private static final long AI_COOLDOWN_MILLIS = 5000L;

    private final AiService aiService;

    public AdminAiController(AiService aiService) {
        this.aiService = aiService;
    }

    // 관리자 확인 다음에 호출: 이 세션이 너무 빨리 다시 불렀으면 true (연타하면 시민용 AI 와 나눠 쓰는 무료 한도가 소진됨)
    private boolean tooFast(HttpSession session) {
        long now = System.currentTimeMillis();
        Long last = (Long) session.getAttribute(AI_TIME_KEY);
        if (last != null && now - last < AI_COOLDOWN_MILLIS) {
            return true;
        }
        session.setAttribute(AI_TIME_KEY, now);
        return false;
    }

    // 현재 데이터를 요약한 인사이트
    @PostMapping("/insight")
    public ResponseEntity<Map<String, String>> insight(HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return fail(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
        if (tooFast(session)) {
            return fail(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }
        try {
            return ResponseEntity.ok(Map.of("answer", aiService.getInsight()));
        } catch (GeminiClient.GeminiException e) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    // 관리자가 직접 한 질문
    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> body, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return fail(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
        if (tooFast(session)) {
            return fail(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }
        try {
            return ResponseEntity.ok(Map.of("answer", aiService.ask(body.get("question"))));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GeminiClient.GeminiException e) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    // 지도에서 노선을 골랐을 때: 그 노선의 미완료 신고 + 이용자 수 예측을 본 분석 (노선번호만 받음)
    // compareRouteNo 를 같이 보내면 두 노선을 비교해서 답하고, question 을 같이 보내면 같은 데이터를 보고 그 질문에 답함
    @PostMapping("/route")
    public ResponseEntity<Map<String, String>> route(@RequestBody Map<String, Object> body, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return fail(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
        if (tooFast(session)) {
            return fail(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }
        Object routeNo = body.get("routeNo");
        Object compare = body.get("compareRouteNo");
        Object question = body.get("question");
        String no = routeNo instanceof String s ? s : null;
        String compareNo = compare instanceof String c ? c : null;
        try {
            // question 이 있으면 그 노선(들)에 대한 질문, 없으면 정해진 분석(네 줄)
            String answer = question == null ? aiService.analyzeRoute(no, compareNo)
                : aiService.askRoute(no, compareNo, question instanceof String q ? q : "");
            return ResponseEntity.ok(Map.of("answer", answer));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GeminiClient.GeminiException e) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
