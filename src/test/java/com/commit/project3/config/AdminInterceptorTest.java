package com.commit.project3.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.commit.project3.dto.MemberDTO;

// /admin/** 가 관리자만 통과하는지 확인: 화면 주소는 이동, JSON 주소는 403
class AdminInterceptorTest {

    private final AdminInterceptor interceptor = new AdminInterceptor();

    private MockHttpServletRequest request(String uri, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (role != null) {
            MemberDTO member = new MemberDTO();
            member.setRole(role);
            request.getSession().setAttribute("loginInfo", member);
        }
        return request;
    }

    @Test
    void 관리자는_화면과_JSON_모두_통과한다() throws Exception {
        for (String uri : new String[] {"/admin", "/admin/report/list", "/admin/api/routes"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(interceptor.preHandle(request(uri, "ADMIN"), response, null), uri);
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void 비로그인이_화면에_오면_로그인으로_보낸다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request("/admin/route", null), response, null));
        assertEquals("/member/login?needLogin=true", response.getRedirectedUrl());
    }

    @Test
    void 일반_회원이_화면에_오면_메인으로_보낸다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request("/admin/report/list", "USER"), response, null));
        assertEquals("/", response.getRedirectedUrl());
    }

    @Test
    void JSON_주소는_비로그인과_일반_회원에게_403을_준다() throws Exception {
        for (String role : new String[] {null, "USER"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request("/admin/api/routes/134/geometry", role), response, null));
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("\"code\":\"FORBIDDEN\""));
            assertNull(response.getRedirectedUrl());
        }
    }

    @Test
    void 비로그인_요청은_세션을_만들지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin");

        interceptor.preHandle(request, new MockHttpServletResponse(), null);

        assertNull(request.getSession(false));
    }
}
