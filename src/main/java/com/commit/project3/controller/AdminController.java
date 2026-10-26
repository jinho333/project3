package com.commit.project3.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    // 카카오맵 JavaScript 키 (application.yaml 의 kakao.js.key), 없으면 빈 값
    @Value("${kakao.js.key:}")
    private String kakaoJsKey;

    // 관리자 대시보드 (노선 지도)
    // TODO: 로그인 구현 후 ROLE 이 관리자인 회원만 접근하도록 제한
    @GetMapping("/admin")
    public String dashboard(Model model) {
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        return "pages/admin/dashboard";
    }
}
