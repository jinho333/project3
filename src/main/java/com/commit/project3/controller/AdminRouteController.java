package com.commit.project3.controller;

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

    private final AdminService adminService;

    public AdminRouteController(AdminService adminService) {
        this.adminService = adminService;
    }

    // 노선 관리 화면: 노선 목록 + 등록 폼
    @GetMapping("/admin/route")
    public String routeList(@RequestParam(defaultValue = "") String keyword, HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("menu", "route");
        model.addAttribute("keyword", keyword.trim());
        model.addAttribute("routeList", adminService.getRouteList(keyword));
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
    public String edit(RouteDTO routeDTO, HttpSession session, RedirectAttributes ra) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        return result(ra, adminService.modifyRoute(routeDTO), "노선 이름이 수정되었습니다.");
    }

    // 노선 삭제
    @PostMapping("/admin/route/delete")
    public String delete(@RequestParam String routeNo, HttpSession session, RedirectAttributes ra) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        return result(ra, adminService.removeRoute(routeNo), "노선이 삭제되었습니다.");
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
