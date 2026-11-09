package com.commit.project3.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commit.project3.dto.RouteOpenCountDTO;
import com.commit.project3.service.AdminService;
import com.commit.project3.service.RoadRouteService;
import com.commit.project3.service.RouteGeometryService;
import com.commit.project3.service.RouteListService;
import com.commit.project3.util.AdminUtil;

// 관리자 화면이 JS 로 부르는 JSON 주소 모음 (/admin/api/...)
// TODO: 로그인 구현 후 ROLE 이 관리자인 회원만 접근하도록 제한
@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final RouteGeometryService routeGeometryService;
    private final RouteListService routeListService;
    private final RoadRouteService roadRouteService;
    private final AdminService adminService;

    public AdminApiController(RouteGeometryService routeGeometryService, RouteListService routeListService,
                              RoadRouteService roadRouteService, AdminService adminService) {
        this.routeGeometryService = routeGeometryService;
        this.routeListService = routeListService;
        this.roadRouteService = roadRouteService;
        this.adminService = adminService;
    }

    // 지도에 보여 줄 노선 목록 (노선번호, 이름): 노선 선 파일이 있는 노선, 이름은 DB(BUS_ROUTE)에서 읽음
    // 200 / 403: 관리자가 아님
    @GetMapping(value = "/routes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> routes(HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
        return ResponseEntity.ok(routeListService.findRouteListJson());
    }

    // 오류 응답 모양: { "code": "...", "message": "..." }
    public record ApiError(String code, String message) {}

    // 노선 하나의 미처리 신고 수: received(접수) + processing(처리중) = total
    public record OpenCount(int received, int processing, int total) {
        static OpenCount of(RouteOpenCountDTO dto) {
            return new OpenCount(dto.getReceivedCnt(), dto.getProcessingCnt(), dto.getReceivedCnt() + dto.getProcessingCnt());
        }
    }

    // 모든 노선의 미처리 신고 수: { "134": { received, processing, total }, ... }
    // 미처리 신고가 없는 노선은 빠져 있으므로 화면에서는 0건으로 보면 됨 (지도를 열 때 한 번 받아 두고 호버에 사용)
    // 200 / 403: 관리자가 아님
    @GetMapping(value = "/routes/report-counts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> reportCounts(HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
        Map<String, OpenCount> counts = new LinkedHashMap<>();
        for (RouteOpenCountDTO dto : adminService.getOpenCountByRoute()) {
            counts.put(dto.getRouteNo(), OpenCount.of(dto));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(counts);
    }

    // 노선 우선순위 (임시 기준: 미처리 건수 -> 가장 오래된 신고 일수): 미처리 신고가 있는 노선만 우선순위 순서로
    // [{ routeNo, received, processing, total, oldestDays }, ...]
    // 200 / 403: 관리자가 아님
    @GetMapping(value = "/routes/priority", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> priority(HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(adminService.getRoutePriority());
    }

    // 노선 하나의 미처리 신고 수 (노선을 클릭했을 때 최신 값으로 갱신하는 용도)
    // 200 / 400: 노선번호 형식이 이상함 / 403: 관리자가 아님
    @GetMapping(value = "/routes/{routeNo}/report-counts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> reportCount(@PathVariable String routeNo, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
        if (!routeGeometryService.isValidRouteNo(routeNo)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ROUTE_NO", "잘못된 노선번호입니다."));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(OpenCount.of(adminService.getOpenCount(routeNo)));
    }

    // 노선 선과 정류장 (GeoJSON)
    // 200: 노선 있음 / 400: 노선번호 형식이 이상함 / 403: 관리자가 아님 / 404: 없는 노선이거나 데이터가 없음
    @GetMapping(value = "/routes/{routeNo}/geometry", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> geometry(@PathVariable String routeNo, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
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
    // 200: 도로 선 있음 / 400: 노선번호 형식이 이상함 / 403: 관리자가 아님 / 404: 도로 선을 쓸 수 없음(화면은 정류장을 이은 직선으로 대체) / 503: 카카오 연결·키 문제
    // 호출할 때마다 카카오 일 한도(1,000건)를 쓰므로 관리자만 부를 수 있게 막음 (관리자 확인을 가장 먼저 해서 비로그인 요청은 카카오까지 가지 못함)
    @GetMapping(value = "/routes/{routeNo}/road", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> road(@PathVariable String routeNo, @RequestParam int brtId, HttpSession session) {
        if (AdminUtil.checkAdmin(session) != null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("FORBIDDEN", "관리자만 사용할 수 있습니다."));
        }
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
