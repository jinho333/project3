package com.commit.project3.controller;

import com.commit.project3.dto.MemberDTO;
import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.service.ReportService;
import com.commit.project3.util.SearchUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Controller
public class ReportController {

    private static final String TOKEN_KEY = "reportToken";                      // 중복 접수 방지용 세션 키

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    // 신고 접수 화면 (로그인한 회원만 이용 가능)
    @GetMapping("/report")
    public String reportForm(@RequestParam(required = false) Integer typeNo, HttpSession session, Model model) {
        // 비로그인이면 로그인 화면으로 보내고, 로그인 화면에서 안내 문구를 띄움
        if (session.getAttribute("loginInfo") == null) {
            return "redirect:/member/login?needLogin=true";
        }
        model.addAttribute("typeGroupList", reportService.getTypeGroupList());
        model.addAttribute("reportToken", issueToken(session));
        // 메인의 유형 TOP 5에서 넘어온 경우: 그 유형을 미리 선택해 둠 (없는 번호면 무시)
        if (reportService.existsType(typeNo)) {
            ReportDTO reportDTO = new ReportDTO();
            reportDTO.setTypeNo(typeNo);
            model.addAttribute("reportDTO", reportDTO);
        }
        return "pages/report/report_form";
    }

    // 신고 목록 화면: 로그인한 회원 본인의 신고만 보여줌 (검색 조건 + 정렬 + 페이징)
    // BindingResult가 있으면 ?typeNo=abc 처럼 형식이 틀린 값이 와도 오류 화면 대신 그 조건만 무시됨
    @GetMapping("/report/list")
    public String reportList(SearchDTO searchDTO, BindingResult bindingResult, HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        // 주소창으로 값을 바꿔 보내도 소용없도록 서버에서 다시 지정·정리
        searchDTO.setMemberNo(loginInfo.getMemberNo());
        // 상태·정렬·개수·검색어·날짜를 정리하고, 검색 결과 위에 보여줄 안내 문구를 받음
        String searchMsg = SearchUtil.normalize(searchDTO, bindingResult);

        // 전체 개수로 페이지 정보를 계산하고, 범위를 벗어난 페이지 번호는 맞춰 줌
        int totalCnt = reportService.getReportCount(searchDTO);
        PageDTO pageDTO = new PageDTO(searchDTO.getPage(), searchDTO.getDisplayDataCnt(), totalCnt);
        searchDTO.setPage(pageDTO.getPage());

        model.addAttribute("reportList", reportService.getReportList(searchDTO));
        model.addAttribute("pageDTO", pageDTO);
        model.addAttribute("typeGroupList", reportService.getTypeGroupList()); // 유형 검색 목록용
        model.addAttribute("searchMsg", searchMsg);
        return "pages/report/report_list";
    }

    // 신고 상세 화면: 본인 신고가 아니거나 없는 번호면 목록으로
    // 숫자가 아닌 주소(/report/abc)는 이 매핑에 걸리지 않아 404 처리됨
    @GetMapping("/report/{reportNo:\\d{1,9}}")
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
                      @RequestParam(value = "reportToken", required = false) String reportToken,
                      @RequestParam(value = "files", required = false) List<MultipartFile> files,
                      @RequestParam(value = "fileDescs", required = false) List<String> fileDescs,
                      HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        // 이미 사용한 토큰이면 같은 신고가 두 번 들어온 것이므로 저장하지 않고 목록으로
        if (!useToken(session, reportToken)) {
            return "redirect:/report/list";
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
            model.addAttribute("reportToken", issueToken(session)); // 다시 제출할 수 있도록 새 토큰 발급
            return "pages/report/report_form";
        }
        return "redirect:/?reported=true";
    }

    // 신고 수정 화면: 본인 신고이면서 '접수' 상태일 때만 열림
    @GetMapping("/report/{reportNo:\\d{1,9}}/edit")
    public String editForm(@PathVariable int reportNo, HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        ReportDTO reportDTO = reportService.getReportDetail(reportNo, loginInfo.getMemberNo());
        if (reportDTO == null || !"RECEIVED".equals(reportDTO.getStatus())) {
            return "redirect:/report/list";
        }
        model.addAttribute("reportDTO", reportDTO);
        model.addAttribute("editMode", true);
        model.addAttribute("typeGroupList", reportService.getTypeGroupList());
        return "pages/report/report_form";
    }

    // 신고 수정 처리: 성공하면 상세 화면으로, 실패하면 오류 문구와 함께 수정 화면을 다시 보여줌
    @PostMapping("/report/{reportNo:\\d{1,9}}/edit")
    public String edit(@PathVariable int reportNo, ReportDTO reportDTO, HttpSession session, Model model) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        reportDTO.setReportNo(reportNo);
        reportDTO.setMemberNo(loginInfo.getMemberNo());

        String errorMsg = reportService.modifyReport(reportDTO);
        if (errorMsg != null) {
            // 이름·전화번호는 수정 대상이 아니라 폼으로 오지 않으므로 DB 값으로 다시 채움
            ReportDTO saved = reportService.getReportDetail(reportNo, loginInfo.getMemberNo());
            if (saved == null) {
                return "redirect:/report/list";
            }
            reportDTO.setReporterName(saved.getReporterName());
            reportDTO.setReporterPhone(saved.getReporterPhone());
            model.addAttribute("errorMsg", errorMsg);
            model.addAttribute("editMode", true);
            model.addAttribute("typeGroupList", reportService.getTypeGroupList());
            return "pages/report/report_form";
        }
        return "redirect:/report/" + reportNo + "?updated=true";
    }

    // 신고 취소(삭제) 처리: 성공하면 목록으로, 못 지우는 상태면 상세 화면으로
    @PostMapping("/report/{reportNo:\\d{1,9}}/delete")
    public String delete(@PathVariable int reportNo, HttpSession session) {
        MemberDTO loginInfo = (MemberDTO) session.getAttribute("loginInfo");
        if (loginInfo == null) {
            return "redirect:/member/login?needLogin=true";
        }
        if (reportService.removeReport(reportNo, loginInfo.getMemberNo())) {
            return "redirect:/report/list?deleted=true";
        }
        return "redirect:/report/" + reportNo;
    }

    // 접수 화면에 심을 새 토큰을 만들어 세션에 저장
    private String issueToken(HttpSession session) {
        String token = UUID.randomUUID().toString();
        session.setAttribute(TOKEN_KEY, token);
        return token;
    }

    // 토큰이 세션의 것과 같으면 지우고 true (한 번만 사용 가능)
    // 같은 세션에서 동시에 두 번 들어와도 한 번만 통과하도록 세션 단위로 잠금
    private boolean useToken(HttpSession session, String token) {
        synchronized (session) {
            Object saved = session.getAttribute(TOKEN_KEY);
            if (token == null || !token.equals(saved)) {
                return false;
            }
            session.removeAttribute(TOKEN_KEY);
            return true;
        }
    }
}
