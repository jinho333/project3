package com.commit.project3.config;

import com.commit.project3.dto.MemberDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

// /admin/** 에 들어오는 요청을 컨트롤러보다 먼저 확인해서 관리자만 통과시킴
// 화면 주소는 비로그인이면 로그인 화면으로, 일반 회원이면 메인으로 보냄
// JSON 주소(/admin/api/**)는 화면으로 보내지 않고 403 과 { "code", "message" } 를 돌려줌
public class AdminInterceptor implements HandlerInterceptor {

    private static final String API_PREFIX = "/admin/api/";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession session = request.getSession(false);
        MemberDTO loginInfo = session == null ? null : (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo != null && "ADMIN".equals(loginInfo.getRole())) {
            return true;
        }
        if (request.getRequestURI().startsWith(request.getContextPath() + API_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"관리자만 사용할 수 있습니다.\"}");
        } else if (loginInfo == null) {
            response.sendRedirect(request.getContextPath() + "/member/login?needLogin=true");
        } else {
            response.sendRedirect(request.getContextPath() + "/");
        }
        return false;
    }
}
