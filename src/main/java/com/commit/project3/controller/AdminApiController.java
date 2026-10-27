package com.commit.project3.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commit.project3.service.RouteGeometryService;
import com.commit.project3.service.RouteListService;

// 관리자 화면이 JS 로 부르는 JSON 주소 모음 (/admin/api/...)
// TODO: 로그인 구현 후 ROLE 이 관리자인 회원만 접근하도록 제한
@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final RouteGeometryService routeGeometryService;
    private final RouteListService routeListService;

    public AdminApiController(RouteGeometryService routeGeometryService, RouteListService routeListService) {
        this.routeGeometryService = routeGeometryService;
        this.routeListService = routeListService;
    }

    // 지도에 보여 줄 노선 목록 (노선번호, 순위, 혼잡 등급, 이름)
    // 지금은 임시 데이터, 나중에 예측 결과로 만든 목록으로 교체
    @GetMapping(value = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
    public String routes() {
        return routeListService.findRouteListJson();
    }

    // 오류 응답 모양: { "code": "...", "message": "..." }
    public record ApiError(String code, String message) {}

    // 노선 선과 정류장 (GeoJSON)
    // 200: 노선 있음 / 400: 노선번호 형식이 이상함 / 404: 없는 노선이거나 데이터가 없음
    @GetMapping(value = "/routes/{routeNo}/geometry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> geometry(@PathVariable String routeNo) {
        if (!routeGeometryService.isValidRouteNo(routeNo)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ROUTE_NO", "잘못된 노선번호입니다."));
        }
        return routeGeometryService.findGeoJson(routeNo)
            .<ResponseEntity<Object>>map(geoJson -> ResponseEntity.ok(geoJson))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ROUTE_NOT_FOUND", routeNo + "번 노선은 없는 노선이거나 데이터가 없습니다.")));
    }
}
