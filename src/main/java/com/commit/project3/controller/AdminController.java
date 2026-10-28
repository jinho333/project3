package com.commit.project3.controller;

import com.commit.project3.dto.StatDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.util.AdminUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AdminController {

    private final AdminService adminService;

    // 카카오맵 JavaScript 키 (application.yaml 의 kakao.js.key), 없으면 빈 값
    @Value("${kakao.js.key:}")
    private String kakaoJsKey;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // 관리자 대시보드: 상태별·노선별·유형별 신고 건수
    @GetMapping("/admin")
    public String dashboard(HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        List<StatDTO> statusStat = adminService.getStatusStat();
        List<StatDTO> routeStat = adminService.getRouteStat();
        List<StatDTO> typeStat = adminService.getTypeStat();

        model.addAttribute("menu", "dashboard");
        model.addAttribute("totalCnt", adminService.getTotalCount());
        model.addAttribute("statusStat", statusStat);
        model.addAttribute("routeStat", routeStat);
        model.addAttribute("typeStat", typeStat);
        // 막대 길이를 정할 때 기준이 되는 가장 큰 값 (0이면 나눗셈 오류가 나므로 최소 1)
        model.addAttribute("statusMax", maxCnt(statusStat));
        model.addAttribute("routeMax", maxCnt(routeStat));
        model.addAttribute("typeMax", maxCnt(typeStat));
        return "pages/admin/dashboard";
    }

    // 버스 혼잡도: 노선 목록 + 지도 + 예측 데이터
    @GetMapping("/admin/congestion")
    public String congestion(HttpSession session, Model model) {
        String redirect = AdminUtil.checkAdmin(session);
        if (redirect != null) {
            return redirect;
        }
        model.addAttribute("menu", "congestion");
        model.addAttribute("kakaoJsKey", kakaoJsKey);
        return "pages/admin/congestion";
    }

    private int maxCnt(List<StatDTO> list) {
        int max = list.stream().mapToInt(StatDTO::getCnt).max().orElse(1);
        return Math.max(max, 1);
    }
}
