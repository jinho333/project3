package com.commit.project3.dto;

import lombok.Data;

@Data
public class RouteDTO {
    private String routeNo;     // 노선번호 (예: 3600)
    private String routeName;   // 노선 이름 (예: 서울-수원)
}
