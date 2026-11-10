package com.commit.project3.controller;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commit.project3.service.HourlyRidershipForecastService;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.RouteGeometryService;

// 노선 하나·날짜 하나의 시간대별 예상 승차 인원 JSON (/admin/api/routes/{노선번호}/ridership/hourly?date=YYYY-MM-DD)
// 일별 예측(AdminRidershipApiController)과 같은 /admin/api 아래이지만, 기존 파일을 건드리지 않으려고 따로 둠
@RestController
@RequestMapping("/admin/api")
public class AdminHourlyRidershipApiController {

    private final HourlyRidershipForecastService hourlyService;
    private final RouteGeometryService routeGeometryService;
    private final AdminService adminService;

    public AdminHourlyRidershipApiController(HourlyRidershipForecastService hourlyService,
                                             RouteGeometryService routeGeometryService, AdminService adminService) {
        this.hourlyService = hourlyService;
        this.routeGeometryService = routeGeometryService;
        this.adminService = adminService;
    }

    // 200: status 가 OK 면 hours 에 24개, 아니면 message 를 화면에 보여 주면 됨 (예측 서버 장애도 200)
    // 400: 노선번호·날짜 형식이 이상하거나 오늘부터 7일 밖의 날짜 / 403: 관리자가 아님 / 404: 노선 관리에 등록되지 않은 노선
    @GetMapping(value = "/routes/{routeNo}/ridership/hourly", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> hourly(@PathVariable String routeNo, @RequestParam String date) {
        LocalDate day;
        try {
            day = LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            day = null;
        }
        if (!routeGeometryService.isValidRouteNo(routeNo) || !HourlyRidershipForecastService.isSelectable(day, LocalDate.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AdminApiController.ApiError("INVALID_REQUEST", "잘못된 요청입니다. 날짜는 오늘부터 7일 안이어야 합니다."));
        }
        if (!adminService.isRegisteredRoute(routeNo)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new AdminApiController.ApiError("ROUTE_NOT_FOUND", "등록되지 않은 노선입니다."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(hourlyService.find(routeNo, day));
    }
}
