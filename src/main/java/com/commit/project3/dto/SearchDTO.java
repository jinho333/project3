package com.commit.project3.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Data
public class SearchDTO {
    private int memberNo;               // 조회할 회원 (컨트롤러에서 로그인 정보로 지정)
    private String status;              // 처리 상태 (RECEIVED / PROCESSING / DONE, 비어 있으면 전체)
    private Integer typeNo;             // 신고 유형 번호 (없으면 전체)

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;        // 접수일 시작 (yyyy-MM-dd)

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;          // 접수일 끝 (yyyy-MM-dd, 이 날짜까지 포함)

    private String keyword;             // 내용 검색어 (비어 있으면 전체)
    private String routeNo;             // 노선번호 (비어 있으면 전체)
    private String sort = "new";        // 정렬: new(최신순) / old(오래된순) / priority(처리 우선순위순, 관리자 목록)
    private List<Integer> priorityNoList;   // priority 정렬일 때 처리할 순서대로 나열한 미처리 신고 번호 (서버가 채움)
    private boolean urgent;             // true 면 긴급 신고(안전 유형, 14일 이상 방치)만 보기 (관리자 목록)
    private List<Integer> urgentNoList; // urgent 일 때 긴급 신고 번호 (서버가 채움)

    private int page = 1;             // 현재 페이지
    private int displayDataCnt = 10;    // 한 페이지에 보여줄 개수

    // LIMIT ... OFFSET 에 쓰는 값: 앞에서 건너뛸 개수
    public int getOffset() {
        return (page - 1) * displayDataCnt;
    }
}
