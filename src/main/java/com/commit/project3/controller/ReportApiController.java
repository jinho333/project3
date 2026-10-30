package com.commit.project3.controller;

import com.commit.project3.dto.RouteDTO;
import com.commit.project3.service.GeminiClient;
import com.commit.project3.service.ReportAiService;
import com.commit.project3.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class ReportApiController {

    private static final String AI_TIME_KEY = "aiRefineAt";         // 마지막 AI 다듬기 시각 (세션)
    private static final long AI_COOLDOWN_MILLIS = 5000L;           // AI 다듬기 사이 최소 간격 (무료 호출 한도 보호)

    private static final String AI_SUGGEST_TIME_KEY = "aiSuggestAt";   // 마지막 문장 자동완성 시각 (세션)

    private final ReportService reportService;
    private final ReportAiService reportAiService;

    public ReportApiController(ReportService reportService, ReportAiService reportAiService) {
        this.reportService = reportService;
        this.reportAiService = reportAiService;
    }

    // 노선선택 모달용 노선 검색 (axios용): 검색어가 없으면 앞에서 50개
    @GetMapping("/api/route")
    public List<RouteDTO> getRouteList(@RequestParam(defaultValue = "") String keyword) {
        return reportService.getRouteList(keyword);
    }

    // 신고 내용 AI 다듬기: 짧은 메모를 신고 문장으로 정리 (로그인한 회원만)
    // 성공: { "refined": "...", "tip": "..." } / 실패: { "message": "..." }
    // [다음 단계] 호출 횟수 제한을 회원 단위로 저장해서 관리 (지금은 세션별 5초 간격만 확인)
    @PostMapping("/api/report/refine")
    public ResponseEntity<Map<String, String>> refine(@RequestBody Map<String, String> body, HttpSession session) {
        if (session.getAttribute("loginInfo") == null) {
            return fail(HttpStatus.UNAUTHORIZED, "로그인 후 사용할 수 있습니다.");
        }
        // 연타하면 무료 호출 한도가 금방 소진되므로 간격을 둠
        long now = System.currentTimeMillis();
        Long last = (Long) session.getAttribute(AI_TIME_KEY);
        if (last != null && now - last < AI_COOLDOWN_MILLIS) {
            return fail(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }
        session.setAttribute(AI_TIME_KEY, now);

        try {
            ReportAiService.Result result = reportAiService.refine(body.get("content"), body.get("typeName"));
            return ResponseEntity.ok(Map.of("refined", result.refined(), "tip", result.tip()));
        } catch (IllegalArgumentException e) {
            return fail(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GeminiClient.GeminiException e) {
            return fail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    // 신고 유형에 맞는 문장 예시 (문장 자동완성): 유형 번호는 DB에서 이름을 찾아 쓰고, 노선은 선택 사항
    // 성공: { "list": ["...", ...] } / 실패: { "message": "..." }
    @PostMapping("/api/report/suggest")
    public ResponseEntity<Object> suggest(@RequestBody Map<String, String> body, HttpSession session) {
        if (session.getAttribute("loginInfo") == null) {
            return failObject(HttpStatus.UNAUTHORIZED, "로그인 후 사용할 수 있습니다.");
        }
        long now = System.currentTimeMillis();
        Long last = (Long) session.getAttribute(AI_SUGGEST_TIME_KEY);
        if (last != null && now - last < AI_COOLDOWN_MILLIS) {
            return failObject(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요.");
        }
        session.setAttribute(AI_SUGGEST_TIME_KEY, now);

        // 숫자가 아닌 유형 번호는 없는 유형으로 취급
        Integer typeNo = null;
        String typeNoText = body.get("typeNo");
        if (typeNoText != null && typeNoText.matches("\\d{1,9}")) {
            typeNo = Integer.valueOf(typeNoText);
        }
        String typeName = reportService.findTypeName(typeNo);

        // 노선은 형식이 맞을 때만 AI 에 알려 줌 (글자를 그대로 프롬프트에 넣지 않기 위해)
        String routeNo = body.get("routeNo");
        if (routeNo == null || !routeNo.matches("[A-Za-z0-9가-힣\\-]{1,20}")) {
            routeNo = null;
        }

        try {
            return ResponseEntity.ok(Map.of("list", reportAiService.suggest(typeName, routeNo)));
        } catch (IllegalArgumentException e) {
            return failObject(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GeminiClient.GeminiException e) {
            return failObject(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private ResponseEntity<Object> failObject(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    private ResponseEntity<Map<String, String>> fail(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
