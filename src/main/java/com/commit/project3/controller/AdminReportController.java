package com.commit.project3.controller;

import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.ReportService;
import com.commit.project3.util.AdminUtil;
import com.commit.project3.util.SearchUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminReportController {

    private final AdminService adminService;
    private final ReportService reportService;     // 유형 검색 목록 재사용

    public AdminReportController(AdminService adminService, ReportService reportService) {
        this.adminService = adminService;
        this.reportService = reportService;
    }

    // 신고 관리 목록: 전체 회원의 신고 (검색 조건 + 정렬 + 페이징)
    @GetMapping("/admin/report/list")
    public String reportList(SearchDTO searchDTO, BindingResult bindingResult, HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        String searchMsg = SearchUtil.normalize(searchDTO, bindingResult);

        int totalCnt = adminService.getReportCount(searchDTO);
        PageDTO pageDTO = new PageDTO(searchDTO.getPage(), searchDTO.getDisplayDataCnt(), totalCnt);
        searchDTO.setPage(pageDTO.getPage());

        model.addAttribute("menu", "report");
        model.addAttribute("reportList", adminService.getReportList(searchDTO));
        model.addAttribute("pageDTO", pageDTO);
        model.addAttribute("typeGroupList", reportService.getTypeGroupList());
        model.addAttribute("searchMsg", searchMsg);
        return "pages/admin/report_list";
    }

    // 신고 처리 화면: 신고 내용 확인 + 상태 변경 + 처리 결과 입력
    @GetMapping("/admin/report/{reportNo:\\d{1,9}}")
    public String reportDetail(@PathVariable int reportNo, HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        ReportDTO reportDTO = adminService.getReportDetail(reportNo);
        if (reportDTO == null) {
            return "redirect:/admin/report/list";
        }
        model.addAttribute("menu", "report");
        model.addAttribute("report", reportDTO);
        return "pages/admin/report_detail";
    }

    // 상태 변경 + 처리 결과 저장: 성공하면 같은 화면으로, 실패하면 오류 문구와 함께 다시 보여줌
    @PostMapping("/admin/report/{reportNo:\\d{1,9}}")
    public String process(@PathVariable int reportNo,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String answer,
                          HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        String errorMsg = adminService.processReport(reportNo, status, answer);
        if (errorMsg == null) {
            return "redirect:/admin/report/" + reportNo + "?saved=true";
        }

        ReportDTO reportDTO = adminService.getReportDetail(reportNo);
        if (reportDTO == null) {
            return "redirect:/admin/report/list";
        }
        reportDTO.setAnswer(answer);   // 입력하던 처리 결과는 그대로 남겨 둠
        model.addAttribute("menu", "report");
        model.addAttribute("report", reportDTO);
        model.addAttribute("errorMsg", errorMsg);
        return "pages/admin/report_detail";
    }
}
