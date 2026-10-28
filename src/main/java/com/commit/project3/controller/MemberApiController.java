package com.commit.project3.controller;

import com.commit.project3.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberApiController {

    private final MemberService memberService;

    public MemberApiController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 아이디 중복 확인 (axios용): true면 이미 사용 중
    @GetMapping("/api/member/dup")
    public boolean isDupMemberId(@RequestParam String memberId) {
        return memberService.isDupMemberId(memberId);
    }
}
