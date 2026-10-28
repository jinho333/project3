package com.commit.project3.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReportDTO {
    private int reportNo;           // 신고 번호
    private Integer memberNo;       // 신고 회원 번호
    private String reporterName;    // 신고자 이름
    private String reporterPhone;   // 신고자 휴대전화번호
    private int typeNo;             // 신고 유형 번호
    private String routeNo;         // 노선 번호 (예: 3600)
    private String content;         // 신고 내용
    private String status;          // 처리 상태 (RECEIVED / PROCESSING / DONE)
    private LocalDateTime regDate;  // 접수일
    private String answer;          // 처리 결과(답변), 아직 없으면 null
    private LocalDateTime answerDate;   // 답변일

    // 조회 화면용 (JOIN으로 가져오는 값)
    private String groupName;       // 대분류 이름
    private String typeName;        // 유형 이름
    private String statusName;      // 처리 상태 이름 (접수 / 처리중 / 완료)
    private List<ReportFileDTO> fileList;   // 첨부파일 목록 (상세 화면에서 사용)
}
