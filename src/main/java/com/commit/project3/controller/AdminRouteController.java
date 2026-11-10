package com.commit.project3.controller;

import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.ForecastRouteSyncService;
import com.commit.project3.service.RouteNameImportService;
import com.commit.project3.util.NumberUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminRouteController {

    private static final int PAGE_SIZE = 15;       // 한 페이지에 보여줄 노선 수

    private final AdminService adminService;
    private final ForecastRouteSyncService forecastRouteSyncService;
    private final RouteNameImportService routeNameImportService;

    public AdminRouteController(AdminService adminService, ForecastRouteSyncService forecastRouteSyncService,
                                RouteNameImportService routeNameImportService) {
        this.adminService = adminService;
        this.forecastRouteSyncService = forecastRouteSyncService;
        this.routeNameImportService = routeNameImportService;
    }

    // 노선 관리 화면: 노선 목록(검색 + 페이징) + 등록 폼
    @GetMapping("/admin/route")
    public String routeList(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "1") String page, Model model) {
        // 범위를 벗어난 page 는 PageDTO 가 맞춰 주므로, 맞춘 값으로 조회 위치를 정함
        PageDTO pageDTO = new PageDTO(toPage(page), PAGE_SIZE, adminService.getRouteCount(keyword));
        model.addAttribute("menu", "route");
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("pageDTO", pageDTO);
        model.addAttribute("routeList", adminService.getRouteList(keyword, (pageDTO.getPage() - 1) * PAGE_SIZE, PAGE_SIZE));
        return "pages/admin/route_list";
    }

    // 노선 등록
    @PostMapping("/admin/route/add")
    public String add(RouteDTO routeDTO, RedirectAttributes ra) {
        return result(ra, adminService.addRoute(routeDTO), "노선이 등록되었습니다.");
    }

    // 노선 이름 수정
    @PostMapping("/admin/route/edit")
    public String edit(RouteDTO routeDTO, @RequestParam(defaultValue = "1") String page,
                       @RequestParam(defaultValue = "") String keyword, RedirectAttributes ra) {
        keepPosition(ra, page, keyword);
        return result(ra, adminService.modifyRoute(routeDTO), "노선 이름이 수정되었습니다.");
    }

    // 노선 삭제
    @PostMapping("/admin/route/delete")
    public String delete(@RequestParam String routeNo, @RequestParam(defaultValue = "1") String page,
                         @RequestParam(defaultValue = "") String keyword, RedirectAttributes ra) {
        keepPosition(ra, page, keyword);
        return result(ra, adminService.removeRoute(routeNo), "노선이 삭제되었습니다.");
    }

    // 주소로 들어온 page 가 숫자가 아니면 오류 화면 대신 1페이지로 봄
    private int toPage(String page) {
        return NumberUtil.toInt(page, 1);
    }

    // 수정·삭제 뒤에 보던 페이지와 검색어 그대로 돌아가도록 주소의 값(?page=&keyword=)으로 넘김
    // 삭제로 마지막 페이지가 비면 PageDTO 가 마지막 페이지로 맞춰 줌
    private void keepPosition(RedirectAttributes ra, String page, String keyword) {
        if (toPage(page) > 1) {
            ra.addAttribute("page", toPage(page));
        }
        if (keyword != null && !keyword.isBlank()) {
            ra.addAttribute("keyword", keyword.trim());
        }
    }

    // 처리 결과를 한 번만 보여주는 안내 문구(flash)로 담고 목록으로 돌아감
    private String result(RedirectAttributes ra, String errorMsg, String successMsg) {
        if (errorMsg != null) {
            ra.addFlashAttribute("errorMsg", errorMsg);
        } else {
            ra.addFlashAttribute("successMsg", successMsg);
        }
        return "redirect:/admin/route";
    }

    // 노선 새로고침: 예측 서버의 노선번호를 노선 표(BUS_ROUTE)에 맞춘 뒤(없던 노선만 "이름 미등록"으로 추가),
    // 울산 BIS 의 이름으로 "이름 미등록" 노선의 이름을 채움. 이미 있는 노선과 이름은 바꾸지 않음
    // 한쪽이 실패해도(예측 서버 꺼짐, 인증키 없음 등) 다른 쪽은 그대로 하고, 안내 문구에 둘의 결과를 함께 보여 줌
    @PostMapping("/admin/route/refresh")
    public String refresh(@RequestParam(defaultValue = "1") String page, @RequestParam(defaultValue = "") String keyword,
                          RedirectAttributes ra) {
        keepPosition(ra, page, keyword);
        ForecastRouteSyncService.Result syncResult = forecastRouteSyncService.sync();          // 번호를 먼저 맞춰야 새 노선도 이름을 받음
        RouteNameImportService.Result nameResult = routeNameImportService.importNames();
        String message = "노선 번호: " + syncResult.message() + " / 노선 이름: " + nameResult.message();
        return result(ra, syncResult.ok() && nameResult.ok() ? null : message, message);
    }
}
