package com.commit.project3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import com.commit.project3.dto.RouteOpenCountDTO;

// 노선 분석(노선 하나 / 두 노선 비교)에서 서버가 쓰는 숫자 문장과 AI 답변이 합쳐지는 모양을 확인
// DB, 예측 서버, Gemini 는 모두 가짜로 대신함
class AiServiceRouteTest {

    private AdminService adminService;
    private GeminiClient gemini;
    private RidershipForecastService ridership;
    private AiService service;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        gemini = mock(GeminiClient.class);
        ridership = mock(RidershipForecastService.class);
        service = new AiService(adminService, gemini, ridership);
    }

    private RouteOpenCountDTO open(int received, int processing) {
        RouteOpenCountDTO dto = new RouteOpenCountDTO();
        dto.setReceivedCnt(received);
        dto.setProcessingCnt(processing);
        return dto;
    }

    // 오늘부터 14일, 날짜 i 일째의 값은 base + i * 10
    private RidershipForecastService.Ridership forecast(String routeNo, int base) {
        List<RidershipForecastService.Day> days = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            days.add(new RidershipForecastService.Day(LocalDate.now().plusDays(i).toString(), "", base + i * 10));
        }
        return new RidershipForecastService.Ridership(routeNo, "OK", null, days, "", "", "", "");
    }

    private void route(String routeNo, int received, int processing, Integer oldestDays, int base) {
        when(adminService.getOpenCount(routeNo)).thenReturn(open(received, processing));
        when(adminService.getOpenOldestDays(routeNo)).thenReturn(oldestDays);
        when(adminService.getOpenTypeStat(routeNo)).thenReturn(List.of());
        when(adminService.getOpenReportText(routeNo, 15)).thenReturn(List.of());
        when(adminService.getOpenReportText(routeNo, 8)).thenReturn(List.of());
        when(ridership.find(routeNo)).thenReturn(forecast(routeNo, base));
    }

    @Test
    void 노선_하나는_숫자_문장은_서버가_쓰고_AI_줄을_이어_붙인다() {
        route("134", 3, 2, 14, 1000);
        when(gemini.generate(anyString(), anyString()))
            .thenReturn("2. 무정차 신고가 많습니다.\n3. 이용이 몰리기 전에 확인하세요.\n4. 운수사에 확인을 요청하세요.");

        String answer = service.analyzeRoute("134", null);

        // 오늘부터 7일 값: 1000, 1010, ... 1060 -> 평균 1030
        assertTrue(answer.contains("1. 현황: 미완료 5건(접수 3건, 처리중 2건), 가장 오래된 신고는 14일째입니다."), answer);
        assertTrue(answer.contains("하루 평균 예상 이용자는 약 1,030명"), answer);
        assertTrue(answer.contains("가장 많은 날은") && answer.contains("1,130명"), answer);
        assertTrue(answer.contains("2. 주된 불편: 무정차 신고가 많습니다."), answer);
        assertTrue(answer.contains("이용이 몰리기 전에 확인하세요."), answer);
        assertTrue(answer.contains("4. 해볼 조치: 운수사에 확인을 요청하세요."), answer);
    }

    @Test
    void 두_노선은_서버가_미완료_신고와_이용자를_비교하고_AI는_3_4번만_쓴다() {
        route("134", 5, 3, 14, 1000);     // 7일 평균 1030
        route("114", 2, 0, 3, 100);       // 7일 평균 130, 약 7.9배
        when(gemini.generate(anyString(), anyString()))
            .thenReturn("3. 선택한 노선은 무정차가 많습니다.\n4. 선택한 노선을 먼저 확인하세요.");

        String answer = service.analyzeRoute("134", "114");

        assertTrue(answer.contains("1. 미완료 신고: 134번 8건(가장 오래된 신고 14일째), 114번 2건(가장 오래된 신고 3일째)."), answer);
        assertTrue(answer.contains("건수가 적은 노선은 우연일 수 있습니다."), answer);
        assertTrue(answer.contains("134번 약 1,030명, 114번 약 130명"), answer);
        assertTrue(answer.contains("134번이 약 7.9배입니다."), answer);
        assertTrue(answer.contains("규모 차이가 커서"), answer);
        assertTrue(answer.contains("3. 두 노선의 차이: 선택한 노선은 무정차가 많습니다."), answer);
        assertTrue(answer.contains("4. 먼저 볼 노선: 선택한 노선을 먼저 확인하세요."), answer);
    }

    @Test
    void 비슷한_규모의_두_노선은_퍼센트로_말하고_주의_문구는_없다() {
        route("134", 5, 0, 7, 1000);      // 평균 1030
        route("114", 5, 0, 7, 900);       // 평균 930 -> 약 11% 많음
        when(gemini.generate(anyString(), anyString())).thenReturn("3. 차이가 없습니다.\n4. 오래된 순으로 보세요.");

        String answer = service.analyzeRoute("134", "114");

        assertTrue(answer.contains("134번이 약 11% 많습니다."), answer);
        assertTrue(!answer.contains("규모 차이가 커서"), answer);
    }

    @Test
    void 같은_노선끼리는_비교할_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> service.analyzeRoute("134", "134"));
    }

    @Test
    void 이상한_노선번호는_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> service.analyzeRoute("../etc", null));
    }

    @Test
    void 예측이_없어도_신고_분석은_된다() {
        when(adminService.getOpenCount("999")).thenReturn(open(0, 0));
        when(adminService.getOpenOldestDays("999")).thenReturn(null);
        when(adminService.getOpenTypeStat("999")).thenReturn(List.of());
        when(adminService.getOpenReportText("999", 15)).thenReturn(List.of());
        when(ridership.find("999")).thenReturn(new RidershipForecastService.Ridership("999", "UNKNOWN_ROUTE",
            "999번 노선은 예측 모델이 학습하지 않은 노선입니다.", List.of(), null, null, null, null));
        when(gemini.generate(anyString(), anyString())).thenReturn("2. 뚜렷한 공통점은 보이지 않습니다.\n3. -\n4. 신고가 없습니다.");

        String answer = service.analyzeRoute("999", null);

        assertTrue(answer.contains("1. 현황: 미완료 신고가 없습니다."), answer);
        assertTrue(answer.contains("3. 예측과 연결: 999번 노선은 예측 모델이 학습하지 않은 노선입니다."), answer);
        assertTrue(!answer.contains("3. 예측과 연결: 999번 노선은 예측 모델이 학습하지 않은 노선입니다. -"), answer);
    }

    @Test
    void AI가_실패해도_사실_줄은_돌려주고_실패한_답은_캐시하지_않는다() {
        route("134", 3, 2, 14, 1000);
        when(gemini.generate(anyString(), anyString())).thenThrow(new GeminiClient.GeminiException("한도를 넘었습니다."));

        String first = service.analyzeRoute("134", null);
        service.analyzeRoute("134", null);

        assertTrue(first.contains("AI 분석을 불러오지 못했습니다. 한도를 넘었습니다."), first);
        assertTrue(first.contains("1. 현황: 미완료 5건"), first);
        verify(gemini, times(2)).generate(anyString(), anyString());
    }

    @Test
    void 성공한_답은_같은_날_같은_건수면_다시_부르지_않는다() {
        route("134", 3, 2, 14, 1000);
        when(gemini.generate(anyString(), anyString())).thenReturn("2. a\n3. b\n4. c");

        String first = service.analyzeRoute("134", null);
        String second = service.analyzeRoute("134", null);

        assertEquals(first, second);
        verify(gemini, times(1)).generate(anyString(), anyString());
    }

    // 날짜를 고정해서(2026-11-09 월요일부터) 요일에 따라 값이 달라지는 14일 예측을 만듦: 월~금 weekday, 토·일 weekend, overrides 의 날짜는 그 값
    private RidershipForecastService.Ridership calendarForecast(String routeNo, int weekday, int weekend, java.util.Map<Integer, Integer> overrides) {
        List<RidershipForecastService.Day> days = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 11, 9);
        for (int i = 0; i < 14; i++) {
            LocalDate d = start.plusDays(i);
            boolean weekend2 = d.getDayOfWeek().getValue() >= 6;
            int value = overrides.containsKey(i) ? overrides.get(i) : (weekend2 ? weekend : weekday);
            days.add(new RidershipForecastService.Day(d.toString(), "", value));
        }
        return new RidershipForecastService.Ridership(routeNo, "OK", null, days, "", "", "", "");
    }

    private void routeWith(String routeNo, RidershipForecastService.Ridership ridershipData) {
        when(adminService.getOpenCount(routeNo)).thenReturn(open(3, 2));
        when(adminService.getOpenOldestDays(routeNo)).thenReturn(5);
        when(adminService.getOpenTypeStat(routeNo)).thenReturn(List.of());
        when(adminService.getOpenReportText(routeNo, 15)).thenReturn(List.of());
        when(ridership.find(routeNo)).thenReturn(ridershipData);
    }

    @Test
    void 평일은_매일_같은_값이면_날짜_묶음으로_말하고_가장_많은_날이라고_하지_않는다() {
        routeWith("134", calendarForecast("134", 9713, 8254, java.util.Map.of()));
        when(gemini.generate(anyString(), anyString())).thenReturn("2. 가\n3. 나\n4. 다");

        String answer = service.analyzeRoute("134", null);

        assertTrue(answer.contains("오늘부터 7일 예상 이용자는 11/9(월)~11/13(금) 하루 약 9,713명, 11/14(토)~11/15(일) 하루 약 8,254명입니다."), answer);
        assertTrue(answer.contains("값이 낮은 구간(11/14(토)~11/15(일))은 높은 구간보다 약 15% 적습니다."), answer);
        assertTrue(!answer.contains("가장 많은 날"), answer);

        // AI 에게 주는 데이터에도 "가장 많은 날까지" 가 없고 날짜 묶음이 들어 있음
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(anyString(), userPrompt.capture());
        assertTrue(userPrompt.getValue().contains("날짜 묶음(값이 같은 연속된 날): 11/9(월)~11/13(금) 하루 약 9,713명"), userPrompt.getValue());
        assertTrue(!userPrompt.getValue().contains("가장 많은 날까지"), "이용이 가장 많은 날까지 는 더 이상 쓰지 않음");
    }

    @Test
    void 평일_중_공휴일처럼_값이_다른_날이_있으면_그_날을_따로_말한다() {
        // 11/10(화)만 8,200명 -> 월 / 화 / 수~금 / 토~일 네 묶음
        routeWith("134", calendarForecast("134", 9713, 8254, java.util.Map.of(1, 8200)));
        when(gemini.generate(anyString(), anyString())).thenReturn("2. 가\n3. 나\n4. 다");

        String answer = service.analyzeRoute("134", null);

        assertTrue(answer.contains("11/9(월) 하루 약 9,713명, 11/10(화) 하루 약 8,200명, 11/11(수)~11/13(금) 하루 약 9,713명, 11/14(토)~11/15(일) 하루 약 8,254명입니다."), answer);
    }

    @Test
    void 지시문에_방향별_설명을_막는_규칙이_들어_있다() {
        routeWith("134", calendarForecast("134", 9713, 8254, java.util.Map.of()));
        routeWith("142", calendarForecast("142", 4200, 3500, java.util.Map.of()));
        when(gemini.generate(anyString(), anyString())).thenReturn("2. 가\n3. 나\n4. 다");

        service.analyzeRoute("134", null);               // 노선 하나
        service.analyzeRoute("134", "142");              // 두 노선
        service.askRoute("134", null, "어느 방향이 더 붐벼?");   // 질문

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(gemini, times(3)).generate(system.capture(), anyString());
        for (String prompt : system.getAllValues()) {
            assertTrue(prompt.contains("방향"), "지시문에 방향 관련 규칙이 있어야 함");
            assertTrue(prompt.contains("양방향 합계") || prompt.contains("방향별 정보는 없습니다"), "노선 전체(양방향 합계) 기준이라고 알려야 함");
        }
        assertTrue(system.getAllValues().get(2).contains("데이터에 방향별 정보는 없습니다"), "질문 지시문: 방향별 질문에는 없다고 답하게 함");
    }

    @Test
    void 두_노선_질문에는_서버가_계산한_비교가_데이터에_들어가고_혼잡_안내_규칙이_있다() {
        routeWith("134", calendarForecast("134", 9713, 8254, java.util.Map.of()));
        routeWith("142", calendarForecast("142", 4200, 3500, java.util.Map.of()));
        when(gemini.generate(anyString(), anyString())).thenReturn("답변");

        service.askRoute("134", "142", "어느 노선이 더 붐벼?");

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(system.capture(), user.capture());
        assertTrue(system.getValue().contains("혼잡도는 데이터에 없어 예상 이용자 수로 비교합니다"), "붐빔을 물으면 혼잡도가 없다고 먼저 밝히게 함");
        assertTrue(system.getValue().contains("같은 말은 쓰지 마세요"), "안내 문구 반복 금지 규칙");
        assertTrue(user.getValue().contains("서버가 계산한 비교"), user.getValue());
        assertTrue(user.getValue().contains("- 예상 이용자: ") && user.getValue().contains("134번 약") && user.getValue().contains("142번 약")
            && user.getValue().contains("134번이 약 2.3배입니다."), user.getValue());
        assertTrue(user.getValue().contains("- 미완료 신고: 134번 5건(가장 오래된 신고 5일째), 142번 5건(가장 오래된 신고 5일째)."), user.getValue());
    }

    @Test
    void 노선_하나_질문에는_서버_비교_문장이_없다() {
        routeWith("134", calendarForecast("134", 9713, 8254, java.util.Map.of()));
        when(gemini.generate(anyString(), anyString())).thenReturn("답변");

        service.askRoute("134", null, "이용자는 어때?");

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(gemini).generate(anyString(), user.capture());
        assertTrue(!user.getValue().contains("서버가 계산한 비교"), user.getValue());
    }
}
