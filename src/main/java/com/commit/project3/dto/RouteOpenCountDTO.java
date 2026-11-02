package com.commit.project3.dto;

import lombok.Data;

@Data
public class RouteOpenCountDTO {
    private String routeNo;         // 노선 번호
    private int receivedCnt;        // 접수 상태(아직 손대지 않은) 신고 수
    private int processingCnt;      // 처리중 상태 신고 수
}
