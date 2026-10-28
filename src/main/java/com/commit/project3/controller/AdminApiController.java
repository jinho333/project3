package com.commit.project3.controller;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commit.project3.service.RoadRouteService;
import com.commit.project3.service.RouteGeometryService;
import com.commit.project3.service.RouteListService;

// 관리자 화면이 JS 로 부르는 JSON 주소 모음 (/admin/api/...)
// TODO: 로그인 구현 후 ROLE 이 관리자인 회원만 접근하도록 제한
@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final RouteGeometryService routeGeometryService;
    private final RouteListService routeListService;
    private final RoadRouteService roadRouteService;

    public AdminApiController(RouteGeometryService routeGeometryService, RouteListService routeListService,
                              RoadRouteService roadRouteService) {
        this.routeGeometryService = routeGeometryService;
        this.routeListService = routeListService;
        this.roadRouteService = roadRouteService;
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

    // 도로를 따라가는 선의 좌표: [[경도, 위도], ...]
    // partial 이 true 이면 카카오 경로와 맞지 않는 끝부분은 정류장을 이은 직선으로 채운 선
    public record RoadPath(int brtId, boolean partial, List<double[]> points) {}

    // 노선 한 방향의 도로 선 (카카오 대중교통 경로를 그때그때 조회, 저장하지 않음)
    // 200: 도로 선 있음 / 400: 노선번호 형식이 이상함 / 404: 도로 선을 쓸 수 없음(화면은 정류장을 이은 직선으로 대체) / 503: 카카오 연결·키 문제
    // 호출할 때마다 카카오 일 한도(1,000건)를 쓰므로 로그인 구현 후 관리자만 부를 수 있게 해야 함
    @GetMapping(value = "/routes/{routeNo}/road", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> road(@PathVariable String routeNo, @RequestParam int brtId) {
        if (!routeGeometryService.isValidRouteNo(routeNo)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ROUTE_NO", "잘못된 노선번호입니다."));
        }
        RoadRouteService.Result result = roadRouteService.findRoadPath(routeNo, brtId);
        if (result.isOk()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RoadPath(brtId, result.partial(), result.points()));
        }
        boolean serverSide = result.reason() == RoadRouteService.Reason.NO_KEY
            || result.reason() == RoadRouteService.Reason.KAKAO_ERROR;
        return ResponseEntity.status(serverSide ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.NOT_FOUND)
            .body(new ApiError(result.reason().name(), "도로 경로를 쓸 수 없습니다: " + result.reason()));
    }
}
