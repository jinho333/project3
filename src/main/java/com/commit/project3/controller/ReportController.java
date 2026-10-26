package com.commit.project3.controller;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.service.ReportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Controller
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 신고 접수 화면 (로그인한 회원만 이용 가능)
    @GetMapping("/report")
    public String reportForm(HttpSession session, Model model) {
        // 비로그인이면 로그인 화면으로 보내고, 로그인 화면에서 안내 문구를 띄움
        if (session.getAttribute("loginInfo") == null) {
            return "redirect:/member/login?needLogin=true";
        }
        model.addAttribute("typeGroupList", reportService.getTypeGroupList());
        return "pages/report/report_form";
    }

    // 신고 목록 화면: 로그인한 회원 본인의 신고만 보여줌 (검색 조건 + 페이징)
    @GetMapping("/report/list")
    public String reportList(SearchDTO searchDTO, HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        // 주소창으로 값을 바꿔 보내도 소용없도록 서버에서 다시 지정
        searchDTO.setMemberNo(loginInfo.getMemberNo());
        searchDTO.setDisplayDataCnt(10);

        // 전체 개수로 페이지 정보를 계산하고, 범위를 벗어난 페이지 번호는 맞춰 줌
        int totalCnt = reportService.getReportCount(searchDTO);
        PageDTO pageDTO = new PageDTO(searchDTO.getPage(), searchDTO.getDisplayDataCnt(), totalCnt);
        searchDTO.setPage(pageDTO.getPage());

        model.addAttribute("reportList", reportService.getReportList(searchDTO));
        model.addAttribute("pageDTO", pageDTO);
        model.addAttribute("typeGroupList", reportService.getTypeGroupList()); // 유형 검색 목록용
        return "pages/report/report_list";
    }

    // 신고 상세 화면: 본인 신고가 아니거나 없는 번호면 목록으로
    @GetMapping("/report/{reportNo}")
    public String reportDetail(@PathVariable int reportNo, HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        ReportDTO reportDTO = reportService.getReportDetail(reportNo, loginInfo.getMemberNo());
        if (reportDTO == null) {
            return "redirect:/report/list";
        }
        model.addAttribute("report", reportDTO);
        return "pages/report/report_detail";
    }

    // 신고 접수 처리: 성공하면 메인으로, 실패하면 오류 문구와 함께 접수 화면을 다시 보여줌
    // 실패 시 입력했던 값은 reportDTO로 화면에 다시 전달됨 (첨부파일은 다시 선택해야 함)
    @PostMapping("/report")
    public String reg(ReportDTO reportDTO,
                      @RequestParam(value = "files", required = false) List<MultipartFile> files,
                      @RequestParam(value = "fileDescs", required = false) List<String> fileDescs,
                      HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        reportDTO.setMemberNo(loginInfo.getMemberNo()); // 신고 회원은 세션의 로그인 정보로 지정

        // 파일이나 설명이 하나도 안 넘어온 경우를 대비해 빈 목록으로 바꿔 줌
        if (files == null) {
            files = List.of();
        }
        if (fileDescs == null) {
            fileDescs = List.of();
        }

        String errorMsg = reportService.regReport(reportDTO, files, fileDescs);
        if (errorMsg != null) {
            model.addAttribute("errorMsg", errorMsg);
            model.addAttribute("typeGroupList", reportService.getTypeGroupList());
            return "pages/report/report_form";
        }
        return "redirect:/?reported=true";
    }
}
