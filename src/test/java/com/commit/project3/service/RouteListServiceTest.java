package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.commit.project3.dto.RouteDTO;
import com.commit.project3.mapper.AdminMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 지도 노선 목록(DB 이름 + 선 파일)과 노선 혼잡 등급(오늘 예상 이용자 수)을 만드는 서비스 확인
// 예측 서버·DB 없이 가짜로 바꿔 끼움
class RouteListServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 9);

    // ---- 노선 목록 ----

    private static class FakeGeometry extends RouteGeometryService {
        private final List<String> routeNos;
        FakeGeometry(String... routeNos) { this.routeNos = List.of(routeNos); }
        @Override public List<String> findRouteNos() { return routeNos; }
    }

    // 하루치 전체 노선의 이용자 수를 한 번에 돌려주는 가짜 예측 서버: 모르는 노선은 빠지고, down 이면 빈 값
    private static class FakeRidership extends RidershipForecastService {
        final Map<String, Integer> riders;
        final AtomicInteger calls = new AtomicInteger();
        volatile boolean down;
        FakeRidership(Map<String, Integer> riders) { super("", ""); this.riders = riders; }
        @Override public Map<String, Integer> findDayRiders(LocalDate day) {
            calls.incrementAndGet();
            return down ? Map.of() : riders;
        }
    }

    private AdminMapper mapper(Function<String, Object> answer) {
        return (AdminMapper) Proxy.newProxyInstance(AdminMapper.class.getClassLoader(), new Class<?>[] {AdminMapper.class},
            (proxy, method, args) -> answer.apply(method.getName()));
    }

    private List<RouteDTO> names(String... pairs) {
        List<RouteDTO> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            RouteDTO r = new RouteDTO();
            r.setRouteNo(pairs[i]);
            r.setRouteName(pairs[i + 1]);
            list.add(r);
        }
        return list;
    }

    private RouteListService service(AdminMapper mapper, RouteGeometryService geometry, FakeRidership ridership, AtomicLong now) {
        return new RouteListService(mapper, geometry, ridership, () -> DAY, now::get);
    }

    @Test
    void DB에_등록된_노선만_번호순으로_DB_이름과_함께_담는다() {
        // 선 파일: 741, 134, 114, 9, 1100 / DB: 134, 114, 9, 3600 (3600 은 선 파일이 없음)
        RouteListService service = service(
            mapper(n -> names("134", "율리공영차고지 ↔ 꽃바위", "114", "율리공영차고지 ↔ 대왕암공원", "9", "시험 노선", "3600", "서울-수원")),
            new FakeGeometry("741", "134", "114", "9", "1100"), new FakeRidership(Map.of()), new AtomicLong());

        JsonNode routes = JSON.readTree(service.findRouteListJson()).path("routes");
        assertEquals(List.of("9", "114", "134"),
            List.of(0, 1, 2).stream().map(i -> routes.get(i).path("routeNo").asString()).toList());   // 9 < 114 (숫자 크기순)
        assertEquals(3, routes.size());                                        // 741, 1100 은 DB 에 없어서, 3600 은 선 파일이 없어서 빠짐
        assertEquals("율리공영차고지 ↔ 대왕암공원", routes.get(1).path("label").asString());
        assertEquals("율리공영차고지 ↔ 꽃바위", routes.get(2).path("label").asString());
    }

    @Test
    void DB에_이름이_비어_있으면_이름_미등록으로_보인다() {
        RouteListService service = service(mapper(n -> names("134", " ")), new FakeGeometry("134"), new FakeRidership(Map.of()), new AtomicLong());
        assertEquals("이름 미등록", JSON.readTree(service.findRouteListJson()).path("routes").get(0).path("label").asString());
    }

    @Test
    void DB에_노선이_하나도_없으면_목록도_비어_있다() {
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry("134", "114"), new FakeRidership(Map.of()), new AtomicLong());
        assertEquals(0, JSON.readTree(service.findRouteListJson()).path("routes").size());
    }

    @Test
    void 지도_화면은_routes_배열의_번호와_이름만_받는다() {
        RouteListService service = service(mapper(n -> names("134", "A")), new FakeGeometry("134"), new FakeRidership(Map.of()), new AtomicLong());
        JsonNode first = JSON.readTree(service.findRouteListJson()).path("routes").get(0);
        assertEquals(List.of("routeNo", "label"), first.propertyNames().stream().toList());
    }

    @Test
    void DB를_못_읽으면_선_파일이_있는_노선을_이름_없이_보여_준다() {
        RouteListService service = service(mapper(n -> {
            throw new DataAccessResourceFailureException("DB 연결 실패");
        }), new FakeGeometry("134"), new FakeRidership(Map.of()), new AtomicLong());

        JsonNode routes = JSON.readTree(service.findRouteListJson()).path("routes");
        assertEquals(1, routes.size());
        assertEquals("이름 미등록", routes.get(0).path("label").asString());
    }

    @Test
    void 선_파일이_없으면_빈_목록이다() {
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry(), new FakeRidership(Map.of()), new AtomicLong());
        assertEquals(0, JSON.readTree(service.findRouteListJson()).path("routes").size());
    }

    @Test
    void 노선번호는_숫자_크기순_그_밖에는_글자순이다() {
        assertTrue(RouteListService.compareRouteNo("9", "10") < 0);
        assertTrue(RouteListService.compareRouteNo("114", "1100") < 0);
        assertTrue(RouteListService.compareRouteNo("134", "142") < 0);
        assertTrue(RouteListService.compareRouteNo("3600", "3600-1") < 0);
    }

    // ---- 혼잡 등급 ----

    @Test
    void 일위를_100으로_한_비율을_혼잡_등급_경계에_대어_등급을_정한다() {
        // 80% 이상 HIGH, 50% 이상 MID, 그 아래 LOW (RouteListService.gradeOf 의 등급 경계와 같음)
        Map<String, String> grades = RouteListService.gradesOf(Map.of(
            "1", 1000, "2", 800, "3", 790, "4", 500, "5", 490));
        assertEquals("HIGH", grades.get("1"));
        assertEquals("HIGH", grades.get("2"));
        assertEquals("MID", grades.get("3"));
        assertEquals("MID", grades.get("4"));
        assertEquals("LOW", grades.get("5"));
    }

    @Test
    void 이용자_수가_하나도_없으면_등급도_없다() {
        assertTrue(RouteListService.gradesOf(Map.of()).isEmpty());
        assertTrue(RouteListService.gradesOf(Map.of("1", 0)).isEmpty());
    }

    @Test
    void 지도_노선의_등급을_이용자_수로_돌려주고_예측이_없는_노선은_비어_있다() {
        FakeRidership ridership = new FakeRidership(Map.of("134", 9713, "114", 9277, "741", 3448));
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry("114", "134", "741", "742"), ridership, new AtomicLong());

        assertEquals("HIGH", service.findGrade("134").orElse(null));
        assertEquals("HIGH", service.findGrade("114").orElse(null));      // 9277 / 9713 = 96%
        assertEquals("LOW", service.findGrade("741").orElse(null));       // 3448 / 9713 = 35%
        assertTrue(service.findGrade("742").isEmpty());                  // 예측 서버가 모르는 노선
        assertTrue(service.findGrade("9999").isEmpty());                 // 지도 노선이 아님
    }

    @Test
    void 결과를_잠깐_기억했다가_시간이_지나면_다시_받는다() {
        FakeRidership ridership = new FakeRidership(Map.of("134", 100, "114", 90));
        AtomicLong now = new AtomicLong(1_000);
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry("134", "114"), ridership, now);

        service.findGrade("134");
        service.findGrade("114");
        assertEquals(1, ridership.calls.get());                  // 노선이 몇 개든 예측 서버를 한 번만 부름 (두 번째 조회는 기억한 값)
        now.addAndGet(9 * 60 * 1000L);
        service.findGrade("134");
        assertEquals(1, ridership.calls.get());                  // 9분: 아직 기억 중
        now.addAndGet(2 * 60 * 1000L);
        service.findGrade("134");
        assertEquals(2, ridership.calls.get());                  // 11분: 다시 받음
    }

    @Test
    void 예측_서버가_안_되면_등급이_없고_금방_다시_시도한다() {
        FakeRidership ridership = new FakeRidership(Map.of("134", 100));
        ridership.down = true;
        AtomicLong now = new AtomicLong(1_000);
        RouteListService service = service(mapper(n -> names("114", "가", "134", "나", "742", "다")),
            new FakeGeometry("114", "134", "742"), ridership, now);

        assertTrue(service.findGrade("134").isEmpty());          // 신고 우선순위는 "예측 없음"으로 이어감
        assertEquals(1, ridership.calls.get());                  // 한 번 부르고 실패
        // 목록 화면은 예측 서버와 상관없이 나옴
        assertEquals(3, JSON.readTree(service.findRouteListJson()).path("routes").size());

        ridership.down = false;
        now.addAndGet(31_000L);                                  // 실패는 30초만 기억
        assertEquals("HIGH", service.findGrade("134").orElse(null));
    }

    // ---- 이용자 규모 (우선순위가 씀) ----

    @Test
    void 이용자_규모는_가장_큰_노선을_100으로_한_비율이다() {
        Map<String, Double> scales = RouteListService.scalesOf(Map.of("134", 9713, "114", 4856, "741", 97));

        assertEquals(100.0, scales.get("134"), 0.001);
        assertEquals(50.0, scales.get("114"), 0.01);
        assertEquals(1.0, scales.get("741"), 0.01);
        assertTrue(RouteListService.scalesOf(Map.of("1", 0)).isEmpty());
    }

    @Test
    void 지도_노선의_이용자_규모와_예측이_없을_때_쓸_중간값을_돌려준다() {
        // 비율: 134=100, 114=50, 741=10, 742=2 → 중간값은 (10 + 50) / 2 = 30
        FakeRidership ridership = new FakeRidership(Map.of("134", 1000, "114", 500, "741", 100, "742", 20));
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry("114", "134", "741", "742", "999"), ridership, new AtomicLong());

        assertEquals(50.0, service.findScale("114").orElse(-1), 0.001);
        assertTrue(service.findScale("999").isEmpty());                  // 예측 서버가 모르는 노선
        assertEquals(30.0, service.neutralScale(), 0.001);
    }

    @Test
    void 이용자_수를_하나도_못_받으면_중간값은_50이다() {
        FakeRidership ridership = new FakeRidership(Map.of());
        RouteListService service = service(mapper(n -> List.of()), new FakeGeometry("134"), ridership, new AtomicLong());

        assertEquals(50.0, service.neutralScale(), 0.001);
        assertTrue(service.findScale("134").isEmpty());
    }

    @Test
    void 중간값은_개수가_홀수면_가운데_짝수면_가운데_둘의_평균이다() {
        assertEquals(3.0, RouteListService.medianOf(List.of(5.0, 1.0, 3.0), 50), 0.001);
        assertEquals(2.5, RouteListService.medianOf(List.of(4.0, 1.0, 2.0, 3.0), 50), 0.001);
    }
}
