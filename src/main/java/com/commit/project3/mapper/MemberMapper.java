package com.commit.project3.mapper;

import com.commit.project3.dto.MemberDTO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MemberMapper {

    // 회원가입
    void insertMember(MemberDTO memberDTO);

    // 아이디 중복 확인 (1이면 중복)
    int isDupMemberId(String memberId);

    // 로그인: 아이디, 비밀번호가 일치하는 회원 1명 조회 (없으면 null)
    MemberDTO getMemberDetail(MemberDTO memberDTO);
}
