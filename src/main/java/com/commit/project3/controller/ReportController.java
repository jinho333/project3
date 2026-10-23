package com.commit.project3.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReportController {

    // 신고 접수 화면
    @GetMapping("/report")
    public String reportForm() {
        return "pages/report/report_form";
    }
}
