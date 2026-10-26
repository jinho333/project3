package com.commit.project3.service;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.mapper.MemberMapper;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberService(MemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    // 아이디 중복 여부 (true면 이미 사용 중)
    public boolean isDupMemberId(String memberId) {
        return memberMapper.isDupMemberId(memberId) > 0;
    }

    // 회원가입
    public void join(MemberDTO memberDTO) {
        memberMapper.insertMember(memberDTO);
    }

    // 로그인: 일치하는 회원이 없으면 null
    public MemberDTO login(MemberDTO memberDTO) {
        return memberMapper.getMemberDetail(memberDTO);
    }
}
