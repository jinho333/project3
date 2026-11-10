package com.commit.project3.dto;

import lombok.Data;

@Data
public class StatDTO {
    private String label;   // 항목 이름 (상태 / 노선번호 / 유형 이름)
    private int cnt;        // 신고 건수
    private Integer id;     // 항목 번호 (유형 통계에서만 채움: 신고 유형 번호, 누르면 그 유형의 신고 목록으로 이동할 때 씀)
}
