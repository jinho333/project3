package com.commit.project3.dto;

import lombok.Data;

@Data
public class StatDTO {
    private String label;   // 항목 이름 (상태 / 노선번호 / 유형 이름)
    private int cnt;        // 신고 건수
}
