package com.commit.project3.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commit.project3.service.RidershipForecastService;
import com.commit.project3.service.RouteGeometryService;

// 노선 하나의 일별 이용자 수 예측 JSON (/admin/api/routes/{노선번호}/ridership)
// AdminApiController 와 같은 /admin/api 아래이지만, 기존 파일을 건드리지 않으려고 따로 둠
@RestController
@RequestMapping("/admin/api")
public class AdminRidershipApiController {

    private final RidershipForecastService ridershipForecastService;
    private final RouteGeometryService routeGeometryService;

    public AdminRidershipApiController(RidershipForecastService ridershipForecastService,
                                       RouteGeometryService routeGeometryService) {
        this.ridershipForecastService = ridershipForecastService;
        this.routeGeometryService = routeGeometryService;
    }

    // 200: status 가 OK 면 days 에 값이 있고, 아니면 message 를 화면에 보여 주면 됨 (예측 서버 장애도 200)
    // 400: 노선번호 형식이 이상함 / 403: 관리자가 아님
    @GetMapping(value = "/routes/{routeNo}/ridership", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> ridership(@PathVariable String routeNo) {
        if (!routeGeometryService.isValidRouteNo(routeNo)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AdminApiController.ApiError("INVALID_REQUEST", "잘못된 요청입니다."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ridershipForecastService.find(routeNo));
    }
}
