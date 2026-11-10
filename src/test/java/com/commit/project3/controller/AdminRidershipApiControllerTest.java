package com.commit.project3.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.commit.project3.service.AdminService;
import com.commit.project3.service.HourlyRidershipForecastService;
import com.commit.project3.service.RidershipForecastService;
import com.commit.project3.service.RouteGeometryService;

// 이용수요 화면의 이용자 수 예측 주소가 노선 관리에 등록된 노선에만 데이터를 주는지 확인 (예측 서버와 DB 는 가짜)
class AdminRidershipApiControllerTest {

    private final RidershipForecastService daily = mock(RidershipForecastService.class);
    private final HourlyRidershipForecastService hourly = mock(HourlyRidershipForecastService.class);
    private final RouteGeometryService geometry = mock(RouteGeometryService.class);
    private final AdminService admin = mock(AdminService.class);
    private final AdminRidershipApiController dailyApi = new AdminRidershipApiController(daily, geometry, admin);
    private final AdminHourlyRidershipApiController hourlyApi = new AdminHourlyRidershipApiController(hourly, geometry, admin);

    private void route(String routeNo, boolean registered) {
        when(geometry.isValidRouteNo(routeNo)).thenReturn(true);
        when(admin.isRegisteredRoute(routeNo)).thenReturn(registered);
    }

    @Test
    void 등록되지_않은_노선은_일별_예측을_주지_않고_예측_서버도_부르지_않는다() {
        route("999", false);

        assertEquals(HttpStatus.NOT_FOUND, dailyApi.ridership("999").getStatusCode());
        verify(daily, never()).find(anyString());
    }

    @Test
    void 등록되지_않은_노선은_시간대별_예측도_주지_않는다() {
        route("999", false);

        assertEquals(HttpStatus.NOT_FOUND, hourlyApi.hourly("999", LocalDate.now().toString()).getStatusCode());
        verify(hourly, never()).find(anyString(), any());
    }

    @Test
    void 등록된_노선은_기존대로_예측을_준다() {
        route("134", true);

        assertEquals(HttpStatus.OK, dailyApi.ridership("134").getStatusCode());
        assertEquals(HttpStatus.OK, hourlyApi.hourly("134", LocalDate.now().toString()).getStatusCode());
    }

    @Test
    void 노선번호_형식이_이상하면_등록_여부를_보기_전에_400이다() {
        when(geometry.isValidRouteNo("../x")).thenReturn(false);

        assertEquals(HttpStatus.BAD_REQUEST, dailyApi.ridership("../x").getStatusCode());
        verify(admin, never()).isRegisteredRoute(anyString());
    }
}
