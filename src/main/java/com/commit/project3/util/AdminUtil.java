package com.commit.project3.util;

import com.commit.project3.dto.MemberDTO;
import jakarta.servlet.http.HttpSession;

public class AdminUtil {

    // 관리자만 들어올 수 있는 화면에서 맨 앞에 호출
    // 통과하면 null, 아니면 보낼 주소를 돌려줌 (비로그인 → 로그인 화면, 일반 회원 → 메인)
    // [다음 단계] 화면이 늘면 Interceptor로 /admin/** 를 한 번에 막는 방식으로 바꿀 것
    public static String checkAdmin(HttpSession session) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        if (!"ADMIN".equals(loginInfo.getRole())) {
            return "redirect:/";
        }
        return null;
    }
}
