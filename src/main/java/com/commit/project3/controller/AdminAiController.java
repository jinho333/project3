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

    private final AiService aiService;

    public AdminAiController(AiService aiService) {
        this.aiService = aiService;
    }

    // 현재 데이터를 요약한 인사이트
    @PostMapping("/insight")
    public ResponseEntity<Map<String, String>> insight(HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return fail(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
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
        try {
            return ResponseEntity.ok(Map.of("answer", aiService.ask(body.get("question"))));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GeminiClient.GeminiException e) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    // 지도에서 노선을 골랐을 때: 그 노선의 미처리 신고 + 1시간 뒤 혼잡도 예측을 본 분석 (노선번호만 받음)
    // question 을 같이 보내면 같은 데이터를 보고 그 질문에 답함. hour 는 시험용(없으면 지금 시각)
    @PostMapping("/route")
    public ResponseEntity<Map<String, String>> route(@RequestBody Map<String, Object> body, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return fail(HttpStatus.FORBIDDEN, "관리자만 사용할 수 있습니다.");
        }
        Object routeNo = body.get("routeNo");
        Object hour = body.get("hour");
        Object question = body.get("question");
        String no = routeNo instanceof String s ? s : null;
        Integer nowHour = hour instanceof Number n ? Integer.valueOf(n.intValue()) : null;
        try {
            // question 이 있으면 그 노선에 대한 질문, 없으면 정해진 분석(네 줄)
            String answer = question == null ? aiService.analyzeRoute(no, nowHour)
                : aiService.askRoute(no, nowHour, question instanceof String q ? q : "");
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
