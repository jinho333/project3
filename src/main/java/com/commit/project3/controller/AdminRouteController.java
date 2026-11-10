package com.commit.project3.controller;

import com.commit.project3.dto.PageDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.util.AdminUtil;
import jakarta.servlet.http.HttpSession;
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

    public AdminRouteController(AdminService adminService) {
        this.adminService = adminService;
    }

    // 노선 관리 화면: 노선 목록(검색 + 페이징) + 등록 폼
    @GetMapping("/admin/route")
    public String routeList(@RequestParam(defaultValue = "") String keyword,
                            @RequestParam(defaultValue = "1") String page, HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
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
    public String add(RouteDTO routeDTO, HttpSession session, RedirectAttributes ra) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        return result(ra, adminService.addRoute(routeDTO), "노선이 등록되었습니다.");
    }

    // 노선 이름 수정
    @PostMapping("/admin/route/edit")
    public String edit(RouteDTO routeDTO, @RequestParam(defaultValue = "1") String page,
                       @RequestParam(defaultValue = "") String keyword, HttpSession session, RedirectAttributes ra) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        keepPosition(ra, page, keyword);
        return result(ra, adminService.modifyRoute(routeDTO), "노선 이름이 수정되었습니다.");
    }

    // 노선 삭제
    @PostMapping("/admin/route/delete")
    public String delete(@RequestParam String routeNo, @RequestParam(defaultValue = "1") String page,
                         @RequestParam(defaultValue = "") String keyword, HttpSession session, RedirectAttributes ra) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        keepPosition(ra, page, keyword);
        return result(ra, adminService.removeRoute(routeNo), "노선이 삭제되었습니다.");
    }

    // 주소로 들어온 page 가 숫자가 아니면 오류 화면 대신 1페이지로 봄
    private int toPage(String page) {
        try {
            return Integer.parseInt(page.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
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
}
