package com.commit.project3.service;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.mapper.MemberMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

    // 화면(JS)의 정규식과 같은 규칙
    private static final String ID_REGEX = "^[a-z0-9]{4,20}$";                          // 영문 소문자/숫자 4~20자
    private static final String PW_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}$"; // 영문+숫자 포함 8~20자
    private static final String NAME_REGEX = "^[가-힣a-zA-Z]{2,20}$";                    // 한글/영문 2~20자
    private static final String PHONE_REGEX = "^01[016789]\\d{7,8}$";                    // 숫자만, 01x로 시작 10~11자리

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberMapper memberMapper, PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    // 아이디 중복 여부 (true면 이미 사용 중)
    public boolean isDupMemberId(String memberId) {
        return memberMapper.isDupMemberId(memberId) > 0;
    }

    // 회원가입: 성공하면 null, 실패하면 오류 문구를 돌려줌
    public String join(MemberDTO memberDTO) {
        // 앞뒤 공백 제거 (비밀번호는 입력한 그대로 둠)
        memberDTO.setMemberId(trim(memberDTO.getMemberId()));
        memberDTO.setMemberName(trim(memberDTO.getMemberName()));
        memberDTO.setMemberPhone(trim(memberDTO.getMemberPhone()));

        // 서버 쪽 형식 검사 (화면의 JS 검사를 우회해도 막기 위함)
        if (!matches(ID_REGEX, memberDTO.getMemberId())) {
            return "아이디는 영문 소문자/숫자 4~20자로 입력해주세요.";
        }
        if (!matches(PW_REGEX, memberDTO.getMemberPw())) {
            return "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.";
        }
        if (!matches(NAME_REGEX, memberDTO.getMemberName())) {
            return "이름은 한글 또는 영문 2~20자로 입력해주세요.";
        }
        if (!matches(PHONE_REGEX, memberDTO.getMemberPhone())) {
            return "휴대전화번호는 숫자만 입력해주세요. (예: 01012345678)";
        }

        // 중복 확인
        if (isDupMemberId(memberDTO.getMemberId())) {
            return "이미 사용 중인 아이디입니다.";
        }

        // 형식 검사가 끝난 뒤에 암호화 (암호화된 값은 60자라 규칙 검사에 쓸 수 없음)
        memberDTO.setMemberPw(passwordEncoder.encode(memberDTO.getMemberPw()));

        try {
            memberMapper.insertMember(memberDTO);
        } catch (DuplicateKeyException e) {
            // 중복 확인과 저장 사이에 같은 아이디가 먼저 가입된 경우 (UK_MEMBER_ID)
            return "이미 사용 중인 아이디입니다.";
        }
        return null;
    }

    // 로그인: 일치하는 회원이 없으면 null
    public MemberDTO login(MemberDTO memberDTO) {
        // 1. 아이디로 회원 조회
        MemberDTO saved = memberMapper.getMemberDetail(trim(memberDTO.getMemberId()));
        if (saved == null) {
            return null; // 없는 아이디
        }
        // 2. 입력한 비밀번호와 DB의 암호화된 값 비교 (입력값, 저장값 순서)
        if (memberDTO.getMemberPw() == null
                || !passwordEncoder.matches(memberDTO.getMemberPw(), saved.getMemberPw())) {
            return null; // 비밀번호 불일치
        }
        return saved;
    }

    // null이면 그대로, 아니면 앞뒤 공백 제거
    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    // null이면 형식 불일치로 처리
    private boolean matches(String regex, String value) {
        return value != null && value.matches(regex);
    }
}
