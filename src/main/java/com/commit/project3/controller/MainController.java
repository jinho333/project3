package com.commit.project3.controller;

import com.commit.project3.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {

    private final ReportService reportService;

    public MainController(ReportService reportService) {
        this.reportService = reportService;
    }

    // localhost:8080 접속 시 메인 페이지(templates/pages/main.html)를 보여줌
    // 신고가 많은 유형 TOP 5를 함께 보여 주고, 누르면 그 유형이 선택된 신고 접수로 이동
    @GetMapping("/")
    public String main(Model model) {
        model.addAttribute("topTypeList", reportService.getTopTypeList());
        return "pages/main";
    }
}
