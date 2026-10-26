package com.commit.project3.controller;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.service.MemberService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 로그인 화면
    @GetMapping("/member/login")
    public String loginForm() {
        return "pages/member/login";
    }

    // 로그인 처리: 성공하면 세션에 loginInfo 저장 후 메인으로
    @PostMapping("/member/login")
    public String login(MemberDTO memberDTO, HttpSession session, Model model) {
        MemberDTO loginInfo = memberService.login(memberDTO);
        if (loginInfo == null) {
            model.addAttribute("errorMsg", "아이디 또는 비밀번호가 올바르지 않습니다.");
            return "pages/member/login";
        }
        loginInfo.setMemberPw(null); // 세션에는 비밀번호를 담지 않음
        session.setAttribute("loginInfo", loginInfo);
        return "redirect:/";
    }

    // 로그아웃: 세션 무효화
    @GetMapping("/member/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    // 회원가입 화면
    @GetMapping("/member/join")
    public String joinForm() {
        return "pages/member/join";
    }

    // 회원가입 처리: 성공하면 로그인 화면으로
    @PostMapping("/member/join")
    public String join(MemberDTO memberDTO, Model model) {
        // 화면에서 중복확인을 했어도 한 번 더 확인
        if (memberService.isDupMemberId(memberDTO.getMemberId())) {
            model.addAttribute("errorMsg", "이미 사용 중인 아이디입니다.");
            return "pages/member/join";
        }
        memberService.join(memberDTO);
        return "redirect:/member/login";
    }
}
