package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.commit.project3.dto.RouteDTO;

// 신고 접수 노선 선택: 오늘 예상 이용자 수 순위(RouteRankService)와 그 순위로 목록을 정렬하는 규칙 확인
// 예측 서버·DB 없이 가짜로 바꿔 끼움
class RouteRankServiceTest {

    // 가짜 예측 서버: 정해 둔 순서를 돌려주고 호출 횟수를 셈. down 이면 빈 목록(못 받음)
    private static class FakeRidership extends RidershipForecastService {
        List<String> ordered;
        final AtomicInteger calls = new AtomicInteger();
        final List<LocalDate> askedDays = new ArrayList<>();
        volatile boolean down;
        FakeRidership(String... ordered) { super("", ""); this.ordered = List.of(ordered); }
        @Override public List<String> findDayRanking(LocalDate day) {
            calls.incrementAndGet();
            askedDays.add(day);
            return down ? List.of() : ordered;
        }
    }

    private RouteDTO route(String no) {
        RouteDTO r = new RouteDTO();
        r.setRouteNo(no);
        r.setRouteName("이름 " + no);
        return r;
    }

    // ---- 순위 ----

    @Test
    void 이용자_수_많은_노선이_0번_순위다() {
        RouteRankService service = new RouteRankService(new FakeRidership("134", "114", "115"), () -> LocalDate.of(2026, 11, 10), () -> 1_000L);
        Map<String, Integer> ranks = service.ranks();
        assertEquals(0, ranks.get("134"));
        assertEquals(1, ranks.get("114"));
        assertEquals(2, ranks.get("115"));
        assertTrue(!ranks.containsKey("9999"));
    }

    @Test
    void 오늘_날짜로_묻고_결과를_10분_기억한다() {
        FakeRidership ridership = new FakeRidership("134", "114");
        AtomicLong now = new AtomicLong(1_000);
        RouteRankService service = new RouteRankService(ridership, () -> LocalDate.of(2026, 11, 10), now::get);

        service.ranks();
        service.ranks();
        assertEquals(1, ridership.calls.get());                      // 두 번째는 기억한 값
        assertEquals(LocalDate.of(2026, 11, 10), ridership.askedDays.get(0));
        now.addAndGet(9 * 60 * 1000L);
        service.ranks();
        assertEquals(1, ridership.calls.get());                      // 9분: 아직 기억 중
        now.addAndGet(2 * 60 * 1000L);
        service.ranks();
        assertEquals(2, ridership.calls.get());                      // 11분: 다시 받음
    }

    @Test
    void 날짜가_바뀌면_시간이_안_지나도_다시_받는다() {
        FakeRidership ridership = new FakeRidership("134");
        AtomicReference<LocalDate> day = new AtomicReference<>(LocalDate.of(2026, 11, 10));
        RouteRankService service = new RouteRankService(ridership, day::get, () -> 1_000L);

        service.ranks();
        day.set(LocalDate.of(2026, 11, 11));
        service.ranks();
        assertEquals(2, ridership.calls.get());
        assertEquals(LocalDate.of(2026, 11, 11), ridership.askedDays.get(1));
    }

    @Test
    void 예측_서버가_안_되면_빈_순위이고_30초_뒤에_다시_시도한다() {
        FakeRidership ridership = new FakeRidership("134");
        ridership.down = true;
        AtomicLong now = new AtomicLong(1_000);
        RouteRankService service = new RouteRankService(ridership, () -> LocalDate.of(2026, 11, 10), now::get);

        assertTrue(service.ranks().isEmpty());
        service.ranks();
        assertEquals(1, ridership.calls.get());                      // 실패도 잠깐 기억해 연타하지 않음
        ridership.down = false;
        now.addAndGet(31_000L);
        assertEquals(0, service.ranks().get("134"));                // 30초 뒤에는 다시 받아 순위가 생김
    }

    // ---- 정렬 규칙 ----

    @Test
    void 순위가_높은_노선이_앞으로_온다() {
        List<RouteDTO> sorted = ReportService.sortByRidership(
            List.of(route("1100"), route("111"), route("134"), route("432")),         // 노선번호순으로 들어옴
            Map.of("134", 0, "432", 1, "111", 2), 50);
        assertEquals(List.of("134", "432", "111", "1100"), sorted.stream().map(RouteDTO::getRouteNo).toList());
    }

    @Test
    void 순위가_없는_노선은_원래_순서_그대로_뒤에_둔다() {
        List<RouteDTO> sorted = ReportService.sortByRidership(
            List.of(route("1100"), route("3600"), route("114"), route("5500"), route("134")),
            Map.of("134", 0, "114", 1), 50);
        assertEquals(List.of("134", "114", "1100", "3600", "5500"), sorted.stream().map(RouteDTO::getRouteNo).toList());
    }

    @Test
    void 순위를_못_받으면_노선번호순_그대로다() {
        List<RouteDTO> sorted = ReportService.sortByRidership(List.of(route("111"), route("114"), route("134")), Map.of(), 50);
        assertEquals(List.of("111", "114", "134"), sorted.stream().map(RouteDTO::getRouteNo).toList());
    }

    @Test
    void 최대_개수까지만_돌려준다() {
        List<RouteDTO> routes = new ArrayList<>();
        Map<String, Integer> ranks = new java.util.HashMap<>();
        for (int i = 0; i < 120; i++) {
            routes.add(route(String.valueOf(100 + i)));
            ranks.put(String.valueOf(100 + i), 119 - i);        // 번호가 클수록 이용자가 많음
        }
        List<RouteDTO> sorted = ReportService.sortByRidership(routes, ranks, 50);
        assertEquals(50, sorted.size());
        assertEquals("219", sorted.get(0).getRouteNo());        // 이용자 수 1위
        assertEquals("170", sorted.get(49).getRouteNo());       // 50위
    }
}
