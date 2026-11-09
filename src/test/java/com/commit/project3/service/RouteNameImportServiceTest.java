package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.commit.project3.service.RouteNameImportService.Row;

// 울산 BIS 의 방향별 줄을 노선 이름 하나로 만드는 규칙 확인 (DB·네트워크 없이)
class RouteNameImportServiceTest {

    private Row row(String no, String name, String direction, String cls) {
        return new Row(no, name, direction, cls);
    }

    @Test
    void 방향이_둘이면_정방향부터_A와_B로_잇는다() {
        // 역방향(2)이 먼저 와도 정방향(1)이 앞에 오도록 방향 번호로 정렬
        String name = RouteNameImportService.buildName(List.of(
            row("114", "114(대왕암공원 방면)", "2", "0"),
            row("114", "114(율리공영차고지종점 방면)", "1", "0")));
        assertEquals("율리공영차고지종점 ↔ 대왕암공원", name);
    }

    @Test
    void 방향_이름이_셋_이상이면_맨_앞과_맨_뒤만_쓴다() {
        // 118: 순환(구분 0) → 중간(구분 1) → 종점 방면(구분 2)
        String name = RouteNameImportService.buildName(List.of(
            row("118", "118(꽃바위(종점) 방면)", "3", "2"),
            row("118", "118(꽃바위(기점) 순환)", "3", "0"),
            row("118", "118(우정동행정복지센터입구 방면)", "3", "1")));
        assertEquals("꽃바위(기점) ↔ 꽃바위(종점)", name);
    }

    @Test
    void 같은_이름이_여러_줄이어도_한_번만_센다() {
        // 142: 같은 이름이 두 줄. 이름은 둘뿐이므로 그대로 잇는다
        String name = RouteNameImportService.buildName(List.of(
            row("142", "142(꽃바위(종점) 방면)", "2", "0"),
            row("142", "142(농소공영차고지 방면)", "1", "0"),
            row("142", "142(농소공영차고지 방면)", "3", "1")));
        assertEquals("농소공영차고지 ↔ 꽃바위(종점)", name);
    }

    @Test
    void 이름이_하나면_순환_여부를_붙인다() {
        assertEquals("꽃바위(기점) 순환", RouteNameImportService.buildName(List.of(
            row("1", "1(꽃바위(기점) 순환)", "3", "0"))));
        assertEquals("남창 방면", RouteNameImportService.buildName(List.of(
            row("2", "2(남창 방면)", "1", "0"))));
    }

    @Test
    void 괄호가_없거나_비어_있으면_이름을_만들지_않는다() {
        assertNull(RouteNameImportService.buildName(List.of(row("9", "9", "1", "0"))));
        assertNull(RouteNameImportService.buildName(List.of(row("9", "9()", "1", "0"))));
        assertNull(RouteNameImportService.buildName(List.of(row("9", "", "1", "0"))));
    }

    @Test
    void 노선번호별로_모으고_번호가_빈_줄은_뺀다() {
        Map<String, String> names = RouteNameImportService.buildNames(List.of(
            row("114", "114(대왕암공원 방면)", "2", "0"),
            row("115", "115(꽃바위(종점) 방면)", "2", "0"),
            row("114", "114(율리공영차고지종점 방면)", "1", "0"),
            row("", "x(없는 번호)", "1", "0")));
        assertEquals(2, names.size());
        assertEquals("율리공영차고지종점 ↔ 대왕암공원", names.get("114"));
        assertEquals("꽃바위(종점) 방면", names.get("115"));
    }

    @Test
    void 이름은_100자를_넘기지_않는다() {
        String longName = "가".repeat(80);
        String name = RouteNameImportService.buildNames(List.of(
            row("7", "7(" + longName + " 방면)", "1", "0"),
            row("7", "7(" + longName + "나 방면)", "2", "0"))).get("7");
        assertTrue(name.length() <= 100);
    }
}
