package com.commit.project3.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MemberDTO {
    private int memberNo;           // 회원 번호
    private String memberId;        // 로그인 아이디
    private String memberPw;        // 비밀번호
    private String memberName;      // 이름
    private String memberPhone;     // 휴대전화번호
    private LocalDateTime regDate;  // 가입일
    private String role;            // 권한 (USER / ADMIN)
}
