package com.commit.project3.controller;

import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportPriorityDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.ReportPriorityService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminReportController {

    private final AdminService adminService;
    private final ReportService reportService;     // 유형 검색 목록 재사용
    private final ReportPriorityService reportPriorityService;

    public AdminReportController(AdminService adminService, ReportService reportService,
                                 ReportPriorityService reportPriorityService) {
        this.adminService = adminService;
        this.reportService = reportService;
        this.reportPriorityService = reportPriorityService;
    }

    // 신고 관리 목록: 전체 회원의 신고 (검색 조건 + 정렬 + 페이징)
    @GetMapping("/admin/report/list")
    public String reportList(SearchDTO searchDTO, BindingResult bindingResult,
                             @RequestParam(required = false) String sort, HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        String searchMsg = SearchUtil.normalize(searchDTO, bindingResult);

        // 처리 우선순위: 미처리 신고를 처리할 순서대로 계산하고, 정렬을 고르지 않았으면 우선순위순으로 보여줌
        List<ReportPriorityDTO> queue = reportPriorityService.getQueue();
        if (sort == null) {
            searchDTO.setSort("priority");
        }
        if ("priority".equals(searchDTO.getSort())) {
            searchDTO.setPriorityNoList(queue.stream().map(p -> p.getReport().getReportNo()).toList());
        }
        Map<Integer, ReportPriorityDTO> priorityMap = new HashMap<>();
        for (ReportPriorityDTO p : queue) {
            priorityMap.put(p.getReport().getReportNo(), p);
        }

        int totalCnt = adminService.getReportCount(searchDTO);
        PageDTO pageDTO = new PageDTO(searchDTO.getPage(), searchDTO.getDisplayDataCnt(), totalCnt);
        searchDTO.setPage(pageDTO.getPage());

        model.addAttribute("menu", "report");
        model.addAttribute("reportList", adminService.getReportList(searchDTO));
        model.addAttribute("pageDTO", pageDTO);
        model.addAttribute("typeGroupList", reportService.getTypeGroupList());
        model.addAttribute("searchMsg", searchMsg);
        model.addAttribute("priorityMap", priorityMap);
        model.addAttribute("summary", reportPriorityService.summarize(queue));
        model.addAttribute("formula", ReportPriorityService.formulaText());
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
        model.addAttribute("priority", reportPriorityService.find(reportNo));   // 끝난 신고면 null
        return "pages/admin/report_detail";
    }

    // 상태 변경 + 처리 결과 저장: 성공하면 같은 화면으로, 실패하면 오류 문구와 함께 다시 보여줌
    // next=true(저장 후 다음 신고) 이면 저장한 뒤 대기열의 다음 신고 처리 화면으로 바로 이동
    @PostMapping("/admin/report/{reportNo:\\d{1,9}}")
    public String process(@PathVariable int reportNo,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String answer,
                          @RequestParam(required = false) String next,
                          HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        // 저장하면 순서가 바뀔 수 있으므로 다음 신고는 저장하기 전 대기열 기준으로 정함
        Integer nextNo = "true".equals(next) ? reportPriorityService.findNextReportNo(reportNo) : null;

        String errorMsg = adminService.processReport(reportNo, status, answer);
        if (errorMsg == null) {
            if ("true".equals(next)) {
                return nextNo != null ? "redirect:/admin/report/" + nextNo : "redirect:/admin/report/list";
            }
            return "redirect:/admin/report/" + reportNo + "?saved=true";
        }

        ReportDTO reportDTO = adminService.getReportDetail(reportNo);
        if (reportDTO == null) {
            return "redirect:/admin/report/list";
        }
        reportDTO.setAnswer(answer);   // 입력하던 처리 결과는 그대로 남겨 둠
        model.addAttribute("menu", "report");
        model.addAttribute("report", reportDTO);
        model.addAttribute("priority", reportPriorityService.find(reportNo));
        model.addAttribute("errorMsg", errorMsg);
        return "pages/admin/report_detail";
    }
}
