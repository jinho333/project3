package com.commit.project3.dto;

// 노선 우선순위 한 줄: 미완료(접수 + 처리중) 신고가 있는 노선
// oldestDays 는 가장 오래된 미완료 신고가 접수된 지 며칠째인지 (모르면 null)
public record RoutePriorityDTO(String routeNo, int received, int processing, int total, Integer oldestDays) {}
