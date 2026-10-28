package com.commit.project3.controller;

import com.commit.project3.dto.RouteDTO;
import com.commit.project3.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ReportApiController {

    private final ReportService reportService;

    public ReportApiController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 노선선택 모달용 노선 검색 (axios용): 검색어가 없으면 앞에서 50개
    @GetMapping("/api/route")
    public List<RouteDTO> getRouteList(@RequestParam(defaultValue = "") String keyword) {
        return reportService.getRouteList(keyword);
    }
}
