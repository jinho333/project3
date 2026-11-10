package com.commit.project3.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.AiService;
import com.commit.project3.service.RoadRouteService;
import com.commit.project3.service.RouteGeometryService;
import com.commit.project3.service.RouteListService;

// 지도 관련 세 주소(노선 목록, 노선 선, 도로 선)의 동작과 관리자 AI 의 연타 방지를 확인 (DB, 카카오, Gemini 는 가짜)
// 관리자만 들어오는지는 AdminInterceptorTest 에서 확인
class AdminApiSecurityTest {

    private final RouteGeometryService geometry = mock(RouteGeometryService.class);
    private final RouteListService list = mock(RouteListService.class);
    private final RoadRouteService road = mock(RoadRouteService.class);
    private final AdminService admin = mock(AdminService.class);
    private final AdminApiController api = new AdminApiController(geometry, list, road, admin);

    private MockHttpSession login(String role) {
        MemberDTO member = new MemberDTO();
        member.setRole(role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loginInfo", member);
        return session;
    }

    // ---- 지도 세 주소 ----

    @Test
    void 노선_목록은_내용을_준다() {
        when(list.findRouteListJson()).thenReturn("{\"routes\":[]}");

        ResponseEntity<Object> res = api.routes();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("{\"routes\":[]}", res.getBody());
    }

    @Test
    void 노선_선은_이상한_노선번호면_400이다() {
        when(geometry.isValidRouteNo("134")).thenReturn(true);
        when(admin.isRegisteredRoute("134")).thenReturn(true);
        when(geometry.findGeoJson("134")).thenReturn(Optional.of("{\"type\":\"FeatureCollection\"}"));

        assertEquals(HttpStatus.OK, api.geometry("134").getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, api.geometry("../x").getStatusCode());
    }

    @Test
    void 도로_선은_관리자가_이상한_노선번호를_보내면_400이고_카카오를_부르지_않는다() {
        when(geometry.isValidRouteNo("../x")).thenReturn(false);

        assertEquals(HttpStatus.BAD_REQUEST, api.road("../x", 1).getStatusCode());
        verify(road, never()).findRoadPath(anyString(), anyInt());
    }

    @Test
    void 노선_관리에_없는_노선은_선과_도로_선과_신고_수를_주지_않는다() {
        when(geometry.isValidRouteNo("999")).thenReturn(true);
        when(admin.isRegisteredRoute("999")).thenReturn(false);

        assertEquals(HttpStatus.NOT_FOUND, api.geometry("999").getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, api.road("999", 1).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, api.reportCount("999").getStatusCode());
        verify(geometry, never()).findGeoJson(anyString());
        verify(road, never()).findRoadPath(anyString(), anyInt());      // 카카오 일 한도를 쓰지 않음
        verify(admin, never()).getOpenCount(anyString());
    }

    // ---- 관리자 AI 호출 간격 ----

    private final AiService ai = mock(AiService.class);
    private final AdminAiController aiController = new AdminAiController(ai);

    @Test
    void AI는_같은_세션에서_연달아_부르면_두번째는_429() {
        when(ai.ask("질문")).thenReturn("답");
        MockHttpSession session = login("ADMIN");

        ResponseEntity<Map<String, String>> first = aiController.ask(Map.of("question", "질문"), session);
        ResponseEntity<Map<String, String>> second = aiController.ask(Map.of("question", "질문"), session);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals("답", first.getBody().get("answer"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, second.getStatusCode());
        assertEquals("잠시 후 다시 시도해 주세요.", second.getBody().get("message"));
        verify(ai).ask("질문");   // 한 번만 호출됨
    }

    @Test
    void AI_간격은_세_주소가_함께_쓰고_세션마다_따로_센다() {
        when(ai.getInsight()).thenReturn("요약");
        MockHttpSession one = login("ADMIN");

        assertEquals(HttpStatus.OK, aiController.insight(one).getStatusCode());
        // 바로 이어서 노선 분석을 눌러도 같은 세션이면 막힘
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, aiController.route(Map.of("routeNo", "134"), one).getStatusCode());
        // 다른 관리자 세션은 영향을 받지 않음
        assertEquals(HttpStatus.OK, aiController.insight(login("ADMIN")).getStatusCode());
    }

    @Test
    void AI는_간격이_지나면_다시_부를_수_있다() {
        when(ai.getInsight()).thenReturn("요약");
        MockHttpSession session = login("ADMIN");
        aiController.insight(session);

        session.setAttribute("adminAiAt", System.currentTimeMillis() - 6000L);   // 6초 전에 불렀다고 봄

        assertEquals(HttpStatus.OK, aiController.insight(session).getStatusCode());
        assertTrue(((Long) session.getAttribute("adminAiAt")) > System.currentTimeMillis() - 1000L, "시각이 새로 기록됨");
    }
}
