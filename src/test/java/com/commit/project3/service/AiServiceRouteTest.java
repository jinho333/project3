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
        assertTrue(answer.contains("1. 현황: 미처리 5건(접수 3건, 처리중 2건), 가장 오래된 신고는 14일째입니다."), answer);
        assertTrue(answer.contains("하루 평균 예상 이용자는 약 1,030명"), answer);
        assertTrue(answer.contains("가장 많은 날은") && answer.contains("1,130명"), answer);
        assertTrue(answer.contains("2. 주된 불편: 무정차 신고가 많습니다."), answer);
        assertTrue(answer.contains("이용이 몰리기 전에 확인하세요."), answer);
        assertTrue(answer.contains("4. 해볼 조치: 운수사에 확인을 요청하세요."), answer);
    }

    @Test
    void 두_노선은_서버가_미처리_신고와_이용자를_비교하고_AI는_3_4번만_쓴다() {
        route("134", 5, 3, 14, 1000);     // 7일 평균 1030
        route("114", 2, 0, 3, 100);       // 7일 평균 130, 약 7.9배
        when(gemini.generate(anyString(), anyString()))
            .thenReturn("3. 선택한 노선은 무정차가 많습니다.\n4. 선택한 노선을 먼저 확인하세요.");

        String answer = service.analyzeRoute("134", "114");

        assertTrue(answer.contains("1. 미처리 신고: 134번 8건(가장 오래된 신고 14일째), 114번 2건(가장 오래된 신고 3일째)."), answer);
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

        assertTrue(answer.contains("1. 현황: 미처리 신고가 없습니다."), answer);
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
        assertTrue(first.contains("1. 현황: 미처리 5건"), first);
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
}
