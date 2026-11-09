package com.commit.project3.dto;

import lombok.Data;

@Data
public class RouteDTO {
    private String routeNo;     // 노선번호 (예: 134)
    private String routeName;   // 노선 이름 (예: 율리공영차고지 ↔ 꽃바위)
}
