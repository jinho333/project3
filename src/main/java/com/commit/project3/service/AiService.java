package com.commit.project3.service;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.StatDTO;
import com.commit.project3.service.RouteFacts.Stats;
import org.springframework.stereotype.Service;

import static com.commit.project3.service.AiPrompts.*;
import static com.commit.project3.service.RouteFactText.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 관리자 AI 비서: 우리 신고 데이터를 모아 Gemini 에게 분석을 맡김
// AI 는 DB 를 직접 보지 않고, 서버가 골라 준 통계 글만 봄 (신고자 이름·전화번호는 보내지 않음)
@Service
public class AiService {

    private static final int DAYS = 7;                    // 대시보드와 같은 기준: 어제까지 최근 7일
    private static final int SAMPLE_LIMIT = 40;           // 신고 내용 샘플 개수
    private static final int SAMPLE_TEXT_LENGTH = 120;    // 샘플 한 건당 최대 글자 수
    private static final int MAX_QUESTION_LENGTH = 300;
    private static final long INSIGHT_CACHE_MILLIS = 10 * 60 * 1000L;   // 인사이트는 10분간 같은 결과 재사용

    // 노선 분석: 노선 하나(미완료 신고 + 이용자 수 예측) 또는 두 노선(선택 노선 + 비교 노선)을 보고 답함
    // 숫자 문장은 서버가 정해진 문장으로 쓰고, AI 는 신고 내용의 공통점·차이, 예측의 의미, 해볼 조치만 씀
    // (숫자와 고정 문구가 틀리거나 빠지지 않고, AI 호출이 실패해도 사실 줄은 그대로 보여 줄 수 있음)
    private static final int ROUTE_SAMPLE_LIMIT = 15;             // 노선 하나일 때 미완료 신고 내용 샘플 개수 (오래된 순)
    private static final int COMPARE_SAMPLE_LIMIT = 8;            // 두 노선일 때 노선마다 샘플 개수
    private static final long ROUTE_CACHE_MILLIS = 10 * 60 * 1000L;
    private static final String ROUTE_NO_REGEX = "^[0-9A-Za-z가-힣]{1,10}$";
    private static final String AI_FAIL_MARK = "AI 분석을 불러오지 못했습니다.";

    // AI 가 쓴 줄을 번호별로 뽑고, 줄 앞에 붙어 있을 수 있는 제목은 뗌 (서버가 제목을 붙임)
    private static final Pattern AI_LINE = Pattern.compile("^\\s*([234])\\s*[.)]\\s*(.*)$");
    private static final Pattern AI_TITLE = Pattern.compile("^(주된 불편|예측과 연결|우선순위|해볼 조치|두 노선의 차이|먼저 볼 노선)\\s*[:：]\\s*");

    private final AdminService adminService;
    private final GeminiClient geminiClient;
    private final RidershipForecastService ridershipForecastService;

    // 인사이트 결과 캐시 (호출 한도를 아끼고, 버튼을 여러 번 눌러도 같은 답을 돌려줌)
    private String cachedInsight;
    private long cachedAt;

    // 노선 분석 캐시: 노선(들), 날짜, 신고 건수가 같을 때만 같은 답을 돌려줌 (신고 처리로 건수가 바뀌면 새로 분석)
    private final Map<String, String> routeCache = new HashMap<>();
    private final Map<String, Long> routeCachedAt = new HashMap<>();

    public AiService(AdminService adminService, GeminiClient geminiClient, RidershipForecastService ridershipForecastService) {
        this.adminService = adminService;
        this.geminiClient = geminiClient;
        this.ridershipForecastService = ridershipForecastService;
    }

    // 질문 없이 현재 데이터를 요약한 인사이트
    public synchronized String getInsight() {
        long now = System.currentTimeMillis();
        if (cachedInsight != null && now - cachedAt < INSIGHT_CACHE_MILLIS) {
            return cachedInsight;
        }
        String question = "이 데이터를 보고 관리자가 지금 알아야 할 핵심 인사이트를 3가지 알려 주세요. "
            + "각 항목은 (발견한 사실 → 왜 중요한지 → 관리자가 해볼 조치) 순서로 두세 문장으로 쓰세요.";
        cachedInsight = geminiClient.generate(SYSTEM_PROMPT, buildPrompt(question));
        cachedAt = now;
        return cachedInsight;
    }

    // 관리자가 직접 한 질문, 질문이 비었거나 너무 길면 IllegalArgumentException
    public String ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해 주세요.");
        }
        question = question.trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("질문은 " + MAX_QUESTION_LENGTH + "자까지 입력할 수 있습니다.");
        }
        return geminiClient.generate(SYSTEM_PROMPT, buildPrompt(question));
    }

    // 노선번호가 이상하면 IllegalArgumentException. 예측 서버를 못 쓰거나 모르는 노선이어도 예외 없이 예측만 빠진 채 돌려줌
    private RouteFacts routeFacts(String routeNo) {
        checkRouteNo(routeNo);
        RidershipForecastService.Ridership ridership = ridershipForecastService.find(routeNo);
        return new RouteFacts(routeNo, adminService.getOpenCount(routeNo), adminService.getOpenOldestDays(routeNo),
            ridership, RouteFacts.statsOf(ridership));
    }

    private void checkRouteNo(String routeNo) {
        if (routeNo == null || !routeNo.matches(ROUTE_NO_REGEX)) {
            throw new IllegalArgumentException("잘못된 노선번호입니다.");
        }
    }

    // 비교 노선은 비어 있으면 없는 것으로 봄. 예측 서버를 부르기 전에 먼저 부르는 검사임. 선택 노선과 같으면 IllegalArgumentException
    private String normalizeCompare(String routeNo, String compareRouteNo) {
        checkRouteNo(routeNo);
        if (compareRouteNo == null || compareRouteNo.isBlank()) {
            return null;
        }
        String compare = compareRouteNo.trim();
        checkRouteNo(compare);
        if (compare.equals(routeNo)) {
            throw new IllegalArgumentException("같은 노선은 비교할 수 없습니다.");
        }
        return compare;
    }

    // 노선 분석: 비교 노선이 없으면 노선 하나의 네 줄, 있으면 두 노선 비교 네 줄
    // 서버가 그 노선(들)의 신고와 예측을 직접 모아서 보냄 (화면에서 받은 숫자는 쓰지 않음)
    // AI 호출이 실패해도 예외로 끝내지 않고, 서버가 쓴 사실 줄은 그대로 돌려줌 (실패한 답은 캐시하지 않음)
    public synchronized String analyzeRoute(String routeNo, String compareRouteNo) {
        String compareNo = normalizeCompare(routeNo, compareRouteNo);
        RouteFacts a = routeFacts(routeNo);
        RouteFacts b = compareNo == null ? null : routeFacts(compareNo);

        String key = LocalDate.now() + "|" + routeNo + "|" + a.open().getReceivedCnt() + "|" + a.open().getProcessingCnt()
            + (b == null ? "" : "|" + compareNo + "|" + b.open().getReceivedCnt() + "|" + b.open().getProcessingCnt());
        long now = System.currentTimeMillis();
        Long at = routeCachedAt.get(key);
        if (at != null && now - at < ROUTE_CACHE_MILLIS) {
            return routeCache.get(key);
        }
        String result = b == null ? analyzeOne(a) : analyzeTwo(a, b);
        if (!result.contains(AI_FAIL_MARK)) {
            routeCache.put(key, result);
            routeCachedAt.put(key, now);
        }
        return result;
    }

    private String analyzeOne(RouteFacts a) {
        String fact1 = factOpen(a);
        String fact3 = factForecast(a);

        Map<Integer, String> ai = Map.of();
        String failure = null;
        try {
            String preface = "1. " + fact1 + "\n3. " + fact3;
            String answer = geminiClient.generate(ROUTE_SYSTEM_PROMPT,
                buildRoutePrompt(List.of(a), preface, "위 데이터로 2, 3, 4번 줄만 쓰세요."));
            ai = parseAiLines(answer);
        } catch (GeminiClient.GeminiException e) {
            failure = e.getMessage();
        }

        String line2 = firstNonBlank(ai.get(2), failure != null
            ? AI_FAIL_MARK + " " + failure : "신고 내용 요약을 만들지 못했습니다.");
        String line4 = firstNonBlank(ai.get(4), "AI 답변이 없어 제안할 수 없습니다.");
        String meaning = ai.get(3);
        StringBuilder line3 = new StringBuilder(fact3);
        if (a.stats() != null && meaning != null && !meaning.isBlank() && !"-".equals(meaning.trim())) {
            line3.append(' ').append(meaning.trim());
        }
        return "1. 현황: " + fact1 + "\n2. 주된 불편: " + line2
            + "\n3. 예측과 연결: " + line3 + "\n4. 해볼 조치: " + line4;
    }

    private String analyzeTwo(RouteFacts a, RouteFacts b) {
        String fact1 = factOpenCompare(a, b);
        String fact2 = factForecastCompare(a, b);

        Map<Integer, String> ai = Map.of();
        String failure = null;
        try {
            String preface = "1. " + fact1 + "\n2. " + fact2;
            String answer = geminiClient.generate(COMPARE_SYSTEM_PROMPT,
                buildRoutePrompt(List.of(a, b), preface, "위 데이터로 3, 4번 줄만 쓰세요."));
            ai = parseAiLines(answer);
        } catch (GeminiClient.GeminiException e) {
            failure = e.getMessage();
        }

        String line3 = firstNonBlank(ai.get(3), failure != null
            ? AI_FAIL_MARK + " " + failure : "두 노선의 차이를 요약하지 못했습니다.");
        String line4 = firstNonBlank(ai.get(4), "AI 답변이 없어 제안할 수 없습니다.");
        return "1. 미완료 신고: " + fact1 + "\n2. 예상 이용자: " + fact2
            + "\n3. 두 노선의 차이: " + line3 + "\n4. 먼저 볼 노선: " + line4;
    }

    // 관리자가 이 노선(과 비교 노선)에 대해 직접 한 질문 (같은 데이터를 보고 답함, 질문이 달라서 캐시하지 않음)
    // 질문이 비었거나 너무 길면 IllegalArgumentException
    public String askRoute(String routeNo, String compareRouteNo, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("질문을 입력해 주세요.");
        }
        question = question.trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("질문은 " + MAX_QUESTION_LENGTH + "자까지 입력할 수 있습니다.");
        }
        String compareNo = normalizeCompare(routeNo, compareRouteNo);
        RouteFacts a = routeFacts(routeNo);
        List<RouteFacts> routes = compareNo == null ? List.of(a) : List.of(a, routeFacts(compareNo));
        return geminiClient.generate(ROUTE_ASK_SYSTEM_PROMPT, buildRoutePrompt(routes, null, question));
    }

    private String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }

    // AI 답변에서 "2. ...", "3. ...", "4. ..." 줄을 번호별로 뽑음 (제목을 붙여 쓴 경우는 제목을 뗌)
    private Map<Integer, String> parseAiLines(String answer) {
        Map<Integer, String> lines = new HashMap<>();
        for (String line : answer.split("\\R")) {
            Matcher m = AI_LINE.matcher(line);
            if (m.matches()) {
                lines.putIfAbsent(Integer.parseInt(m.group(1)), AI_TITLE.matcher(m.group(2).trim()).replaceFirst(""));
            }
        }
        return lines;
    }

    // preface: AI 가 서버가 이미 쓴 문장을 알고 이어 쓰도록 보여 주는 문장 (없으면 null)
    // routes 가 둘이면 첫 번째가 선택한 노선, 두 번째가 비교 노선
    private String buildRoutePrompt(List<RouteFacts> routes, String preface, String question) {
        StringBuilder sb = new StringBuilder();
        if (preface != null) {
            sb.append("[이미 작성된 문장]\n").append(preface).append("\n\n");
        }
        sb.append("[데이터]\n");
        sb.append("오늘: ").append(LocalDate.now()).append("\n");
        if (routes.size() == 2 && preface == null) {
            sb.append("서버가 계산한 비교 (선택한 노선, 비교 노선 순서, 이 숫자를 그대로 쓰세요):\n");
            sb.append("- 미완료 신고: ").append(factOpenCompare(routes.get(0), routes.get(1))).append("\n");
            sb.append("- 예상 이용자: ").append(factForecastCompare(routes.get(0), routes.get(1))).append("\n");
        }
        int limit = routes.size() > 1 ? COMPARE_SAMPLE_LIMIT : ROUTE_SAMPLE_LIMIT;
        for (int i = 0; i < routes.size(); i++) {
            RouteFacts f = routes.get(i);
            sb.append("\n=== ").append(routes.size() > 1 ? (i == 0 ? "선택한 노선 " : "비교 노선 ") : "노선 ")
              .append(f.routeNo()).append("번 ===\n");
            appendRouteData(sb, f, limit);
        }
        sb.append("\n[질문]\n").append(question);
        return sb.toString();
    }

    private void appendRouteData(StringBuilder sb, RouteFacts f, int sampleLimit) {
        String routeNo = f.routeNo();
        sb.append("미완료 신고(접수 + 처리중): ").append(f.total()).append("건 (접수 ").append(f.open().getReceivedCnt())
          .append("건, 처리중 ").append(f.open().getProcessingCnt()).append("건)\n");
        if (f.oldestDays() != null) {
            sb.append("가장 오래된 미완료 신고: ").append(f.oldestDays() == 0 ? "오늘 접수" : f.oldestDays() + "일째").append("\n");
        }
        sb.append("신고 항목에 정류장은 저장되지 않음 (신고 내용 글에만 이름이 적혀 있을 수 있음)\n");

        sb.append("\n미완료 신고 유형별 건수:\n");
        appendStat(sb, adminService.getOpenTypeStat(routeNo), "건");

        sb.append("\n미완료 신고 목록 (오래된 순, 최대 ").append(sampleLimit).append("건, 유형 / 상태 / 접수일 / 내용):\n");
        List<ReportDTO> samples = adminService.getOpenReportText(routeNo, sampleLimit);
        if (samples.isEmpty()) {
            sb.append("- 없음\n");
        }
        for (ReportDTO r : samples) {
            sb.append("- ").append(r.getTypeName()).append(" / ")
              .append("PROCESSING".equals(r.getStatus()) ? "처리중" : "접수")
              .append(" / ").append(regDateText(r)).append(" / ").append(cleanContent(r.getContent())).append("\n");
        }

        sb.append("\n[이용자 수 예측] (노선 전체, 양방향 합계, 하루 이용자 수(명), 합성데이터로 학습한 통계적 경향)\n");
        Stats s = f.stats();
        if (s == null) {
            sb.append("예측 없음: ").append(f.ridership().message() != null ? f.ridership().message() : NO_FORECAST_NOTICE).append("\n");
            return;
        }
        sb.append("오늘부터 ").append(Math.min(AVG_DAYS, s.dayCount())).append("일 하루 평균: ").append(fmt(s.avg7())).append("명\n");
        sb.append("날짜 묶음(값이 같은 연속된 날): ").append(runsText(s)).append("\n");
        if (s.runs().size() > MAX_RUNS) {                // 날마다 값이 다를 때만 가장 많은/적은 날을 알려 줌
            sb.append("이용이 가장 많은 날: ").append(dateText(s.max())).append(" ").append(fmt(s.max().predicted())).append("명\n");
            sb.append("이용이 가장 적은 날: ").append(dateText(s.min())).append(" ").append(fmt(s.min().predicted())).append("명\n");
        }
        sb.append("날짜별 예상:");
        for (RidershipForecastService.Day d : f.ridership().days()) {
            sb.append(' ').append(dateText(d)).append(' ').append(fmt(d.predicted())).append("명,");
        }
        sb.append("\n");
        if (f.ridership().notice() != null && !f.ridership().notice().isBlank()) {
            sb.append("예측 서버 안내: ").append(f.ridership().notice()).append("\n");
        }
    }

    // 접수일: "2026-10-12(21일째)" 처럼 날짜와 며칠 지났는지 (접수일을 모르면 "접수일 미상")
    private String regDateText(ReportDTO r) {
        if (r.getRegDate() == null) {
            return "접수일 미상";
        }
        LocalDate date = r.getRegDate().toLocalDate();
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        return date + (days <= 0 ? "(오늘)" : "(" + days + "일째)");
    }

    // 통계와 신고 내용 샘플을 글로 정리해 질문 앞에 붙임
    private String buildPrompt(String question) {
        StringBuilder sb = new StringBuilder();
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = LocalDate.now().minusDays(DAYS);

        sb.append("[데이터]\n");
        sb.append("집계 기간: ").append(start).append(" ~ ").append(end).append(" (오늘 제외)\n");
        sb.append("기간 내 접수된 신고: ").append(adminService.getPeriodCount(DAYS)).append("건\n");
        sb.append("기간 내 처리 완료: ").append(adminService.getDoneCount(DAYS)).append("건\n");

        sb.append("\n현재 처리해야 할 일(기간과 무관):\n");
        appendStat(sb, adminService.getStatusStat(), "건");

        sb.append("\n날짜별 접수 건수:\n");
        appendStat(sb, adminService.getDailyStat(DAYS), "건");

        sb.append("\n신고가 많은 노선 TOP 10:\n");
        appendStat(sb, adminService.getRouteStat(DAYS), "건");

        sb.append("\n신고가 많은 유형 TOP 10:\n");
        appendStat(sb, adminService.getTypeStat(DAYS), "건");

        sb.append("\n최근 신고 내용 샘플 (노선 / 유형 / 상태 / 내용, 최대 ").append(SAMPLE_LIMIT).append("건):\n");
        List<ReportDTO> samples = adminService.getRecentReportText(DAYS, SAMPLE_LIMIT);
        if (samples.isEmpty()) {
            sb.append("- 없음\n");
        }
        for (ReportDTO r : samples) {
            sb.append("- ").append(r.getRouteNo()).append(" / ").append(r.getTypeName())
              .append(" / ").append(r.getStatus()).append(" / ").append(cleanContent(r.getContent())).append("\n");
        }

        sb.append("\n[질문]\n").append(question);
        return sb.toString();
    }

    private void appendStat(StringBuilder sb, List<StatDTO> list, String unit) {
        if (list.isEmpty()) {
            sb.append("- 없음\n");
        }
        for (StatDTO s : list) {
            sb.append("- ").append(s.getLabel()).append(": ").append(s.getCnt()).append(unit).append("\n");
        }
    }

    // 신고 내용 정리: 줄바꿈 제거, 전화번호 가리기, 길이 자르기
    private String cleanContent(String content) {
        if (content == null) {
            return "";
        }
        String text = content.replaceAll("\\s+", " ")
            .replaceAll("0\\d{1,2}[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}", "[전화번호]")
            .trim();
        return text.length() > SAMPLE_TEXT_LENGTH ? text.substring(0, SAMPLE_TEXT_LENGTH) + "…" : text;
    }
}
