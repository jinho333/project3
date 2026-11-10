package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportPriorityDTO;
import com.commit.project3.mapper.AdminMapper;

// 이용자 규모(가장 큰 노선 대비 %)가 처리 우선순위 점수에 들어가는 방식을 확인 (DB 와 예측 서버는 가짜)
class ReportPriorityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private AdminMapper mapper;
    private RouteListService routes;
    private ReportPriorityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AdminMapper.class);
        routes = mock(RouteListService.class);
        service = new ReportPriorityService(mapper, routes);
        when(mapper.selectRecentRouteTypeCount()).thenReturn(List.of());
    }

    private ReportDTO report(int no, String routeNo) {
        ReportDTO r = new ReportDTO();
        r.setReportNo(no);
        r.setRouteNo(routeNo);
        r.setTypeNo(1);
        r.setTypeName("무정차 통과");
        r.setRegDate(NOW);
        return r;
    }

    @Test
    void 이용자가_많은_노선의_신고가_점수가_높고_점수_차이는_규모_차이에_가중치를_곱한_값이다() {
        when(mapper.selectOpenReportAll()).thenReturn(List.of(report(1, "SMALL"), report(2, "BIG")));
        when(routes.findScale("BIG")).thenReturn(OptionalDouble.of(100));
        when(routes.findScale("SMALL")).thenReturn(OptionalDouble.of(10));
        when(routes.neutralScale()).thenReturn(30.0);

        List<ReportPriorityDTO> queue = service.getQueue(NOW);

        assertEquals(2, queue.get(0).getReport().getReportNo());
        assertEquals(100.0, queue.get(0).getScaleScore(), 0.001);
        assertEquals(27.0, queue.get(0).getScore() - queue.get(1).getScore(), 0.11);     // 0.3 × (100 - 10)
    }

    @Test
    void 예측이_없는_노선은_중간값으로_계산하고_점수에_예측_없음을_표시한다() {
        when(mapper.selectOpenReportAll()).thenReturn(List.of(report(1, "NONE")));
        when(routes.findScale("NONE")).thenReturn(OptionalDouble.empty());
        when(routes.neutralScale()).thenReturn(30.0);

        ReportPriorityDTO p = service.getQueue(NOW).get(0);

        assertNull(p.getScaleScore());
        assertEquals("예측 없음", p.getScaleLabel());
        assertTrue(p.getReasonList().contains("이용자 수 예측 없음"));
        // 무정차 70×0.4 + 중간값 30×0.3 + 방치 0 + 누적(같은 노선 1건 → 20)×0.1 = 28 + 9 + 2
        assertEquals(39.0, p.getScore(), 0.11);
    }

    @Test
    void 최다_노선의_절반_이상이면_이유로_보여_준다() {
        when(mapper.selectOpenReportAll()).thenReturn(List.of(report(1, "BIG"), report(2, "MID"), report(3, "LOW")));
        when(routes.findScale("BIG")).thenReturn(OptionalDouble.of(100));
        when(routes.findScale("MID")).thenReturn(OptionalDouble.of(50));
        when(routes.findScale("LOW")).thenReturn(OptionalDouble.of(49));
        when(routes.neutralScale()).thenReturn(30.0);

        List<ReportPriorityDTO> queue = service.getQueue(NOW);

        assertTrue(queue.get(0).getReasonList().contains("이용자 최다 노선의 100%"));
        assertTrue(queue.get(1).getReasonList().contains("이용자 최다 노선의 50%"));
        assertTrue(queue.get(2).getReasonList().stream().noneMatch(t -> t.startsWith("이용자 최다")));
    }
}
