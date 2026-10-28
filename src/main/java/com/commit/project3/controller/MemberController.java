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

    // 로그인 화면 (이미 로그인했으면 메인으로)
    @GetMapping("/member/login")
    public String loginForm(HttpSession session) {
        if (session.getAttribute("loginInfo") != null) {
            return "redirect:/";
        }
        return "pages/member/login";
    }

    // 로그인 처리: 성공하면 세션에 loginInfo 저장 후 메인으로
    // 실패하면 입력했던 아이디는 memberDTO로 화면에 다시 전달됨
    @PostMapping("/member/login")
    public String login(MemberDTO memberDTO, HttpSession session, Model model) {
        if (session.getAttribute("loginInfo") != null) {
            return "redirect:/";
        }
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

    // 회원가입 화면 (이미 로그인했으면 메인으로)
    @GetMapping("/member/join")
    public String joinForm(HttpSession session) {
        if (session.getAttribute("loginInfo") != null) {
            return "redirect:/";
        }
        return "pages/member/join";
    }

    // 회원가입 처리: 성공하면 로그인 화면으로
    // 실패하면 입력했던 값(비밀번호 제외)은 memberDTO로 화면에 다시 전달됨
    @PostMapping("/member/join")
    public String join(MemberDTO memberDTO, HttpSession session, Model model) {
        if (session.getAttribute("loginInfo") != null) {
            return "redirect:/";
        }
        String errorMsg = memberService.join(memberDTO);
        if (errorMsg != null) {
            model.addAttribute("errorMsg", errorMsg);
            return "pages/member/join";
        }
        return "redirect:/member/login";
    }
}
