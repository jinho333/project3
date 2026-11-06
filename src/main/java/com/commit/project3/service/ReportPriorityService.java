package com.commit.project3.service;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportPriorityDTO;
import com.commit.project3.dto.StatDTO;
import com.commit.project3.mapper.AdminMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

// 미처리 신고의 처리 우선순위
//   점수 = 0.4×심각도(S) + 0.3×이용자규모(U) + 0.2×방치(W) + 0.1×누적(N)   (네 항목 모두 0~100점)
//   정렬 = 긴급 구간(안전 유형, 또는 14일 이상 방치) 먼저 → 점수 높은 순 → 오래된 신고 먼저
// 규칙 검증에서 드러난 문제를 고친 부분:
//   - 예측이 없는 노선은 "한산(0점)"이 아니라 중간값(50)으로 계산 (모르는 것을 낮다고 보지 않음)
//   - 같은 노선·유형의 최근 4주 반복 신고도 누적 점수에 반영 (화면은 반복을 강조하는데 점수는 무시하던 불일치)
//   - 14일 이상 방치된 신고는 긴급 구간으로 올림 (긴급 신고가 계속 들어와도 오래된 신고가 끝없이 밀리지 않게)
// 가중치는 시작값이므로, 실제 신고로 관리자가 매기는 순서와 비교해 가며 아래 상수만 고치면 됨
// 이용자규모(U)의 혼잡도는 아직 임시값(RouteForecastService)이라, 실제 값이 들어오면 그쪽만 바뀜
@Service
public class ReportPriorityService {

    private static final double WEIGHT_SEVERITY = 0.4;
    private static final double WEIGHT_SCALE = 0.3;
    private static final double WEIGHT_WAIT = 0.2;
    private static final double WEIGHT_REPEAT = 0.1;

    private static final int SCALE_WINDOW_HOURS = 6;    // 이용자규모: 처리 시점부터 앞으로 몇 시간의 평균을 볼지
    private static final int WAIT_FULL_DAYS = 7;        // 이만큼 방치하면 방치 점수 만점
    private static final int REPEAT_FULL_CNT = 5;       // 같은 노선에 이만큼 쌓이면 누적 점수 만점
    private static final int REPEAT_TYPE_FULL_CNT = 3;  // 최근 4주 같은 노선·유형이 이만큼이면 누적 점수 만점
    private static final int AGING_DAYS = 14;           // 이만큼 방치되면 긴급 구간으로 올림
    private static final double NEUTRAL_SCALE = 50;     // 혼잡도 예측이 없을 때 쓰는 이용자규모 (중간값)
    private static final int DEFAULT_SEVERITY = 30;     // 유형을 모를 때

    // 심각도(S): 신고 유형 이름으로 정함 (유형 번호는 DB 입력 순서에 따라 달라질 수 있어 이름을 씀)
    private static final Map<String, Integer> SEVERITY = Map.ofEntries(
        Map.entry("난폭운전(급출발·급정거 등)", 100),
        Map.entry("신호위반", 100),
        Map.entry("개문발차 운행", 100),
        Map.entry("차량 정비 불량", 80),
        Map.entry("무정차 통과", 70),
        Map.entry("승차 거부", 70),
        Map.entry("첫차·막차 미운행", 70),
        Map.entry("배차간격·운행시간 미준수", 60),
        Map.entry("운행 중 흡연", 60),
        Map.entry("요금 부당 징수", 60),
        Map.entry("정류장 외 승·하차", 50),
        Map.entry("교통카드 단말기 오류", 50),
        Map.entry("환승 할인 미적용", 50),
        Map.entry("냉·난방 고장", 50),
        Map.entry("기사 불친절(반말·욕설 등)", 40),
        Map.entry("안내방송 미실시", 30),
        Map.entry("차량 내부 청결 불량", 30),
        Map.entry("기타 불편 사항", 20));

    // 긴급(안전): 사고로 이어질 수 있는 유형은 점수와 상관없이 맨 앞 구간에서 처리
    private static final Set<String> URGENT = Set.of("난폭운전(급출발·급정거 등)", "신호위반", "개문발차 운행");

    private final AdminMapper adminMapper;
    private final RouteForecastService routeForecastService;

    public ReportPriorityService(AdminMapper adminMapper, RouteForecastService routeForecastService) {
        this.adminMapper = adminMapper;
        this.routeForecastService = routeForecastService;
    }

    // 처리 대기열: 지금 처리해야 할 순서대로 (미처리 신고 전부)
    public List<ReportPriorityDTO> getQueue() {
        return getQueue(LocalDateTime.now());
    }

    // now 는 시험용으로 바꿔 부를 수 있음
    public List<ReportPriorityDTO> getQueue(LocalDateTime now) {
        List<ReportDTO> openList = adminMapper.selectOpenReportAll();

        Map<String, Integer> routeCnt = new HashMap<>();
        for (ReportDTO r : openList) {
            routeCnt.merge(r.getRouteNo(), 1, Integer::sum);
        }
        Map<String, OptionalDouble> scaleCache = new HashMap<>();   // 같은 노선은 한 번만 계산
        Map<String, Integer> recentCnt = new HashMap<>();           // "노선|유형번호" → 최근 4주 신고 수
        for (StatDTO stat : adminMapper.selectRecentRouteTypeCount()) {
            recentCnt.put(stat.getLabel(), stat.getCnt());
        }

        List<ReportPriorityDTO> queue = new ArrayList<>();
        for (ReportDTO r : openList) {
            OptionalDouble scale = scaleCache.computeIfAbsent(r.getRouteNo(),
                no -> routeForecastService.averageAhead(no, now.getHour(), SCALE_WINDOW_HOURS));
            int recent = recentCnt.getOrDefault(r.getRouteNo() + "|" + r.getTypeNo(), 1);
            queue.add(evaluate(r, routeCnt.get(r.getRouteNo()), recent, scale, now));
        }

        // 긴급 먼저 → 점수 높은 순 → 오래된 신고 먼저
        queue.sort(Comparator
            .comparing(ReportPriorityDTO::isUrgent).reversed()
            .thenComparing(Comparator.comparingDouble(ReportPriorityDTO::getScore).reversed())
            .thenComparing(p -> p.getReport().getReportNo()));
        for (int i = 0; i < queue.size(); i++) {
            queue.get(i).setRank(i + 1);
        }
        return queue;
    }

    // 신고 하나의 우선순위: 이미 끝난 신고이거나 없는 번호면 null
    public ReportPriorityDTO find(int reportNo) {
        for (ReportPriorityDTO p : getQueue()) {
            if (p.getReport().getReportNo() == reportNo) {
                return p;
            }
        }
        return null;
    }

    // 지금 신고 다음으로 처리할 신고 번호: 대기열에서 바로 다음, 마지막이면 맨 앞(자기 자신 제외), 없으면 null
    public Integer findNextReportNo(int reportNo) {
        List<ReportPriorityDTO> queue = getQueue();
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getReport().getReportNo() == reportNo) {
                if (i + 1 < queue.size()) {
                    return queue.get(i + 1).getReport().getReportNo();
                }
                break;
            }
        }
        for (ReportPriorityDTO p : queue) {
            if (p.getReport().getReportNo() != reportNo) {
                return p.getReport().getReportNo();
            }
        }
        return null;
    }

    private ReportPriorityDTO evaluate(ReportDTO r, int sameRouteCnt, int recentSameCnt, OptionalDouble scale, LocalDateTime now) {
        double severity = SEVERITY.getOrDefault(r.getTypeName(), DEFAULT_SEVERITY);
        double waitDaysExact = Duration.between(r.getRegDate(), now).toMinutes() / (60.0 * 24);
        double wait = Math.min(100, Math.max(0, waitDaysExact) / WAIT_FULL_DAYS * 100);
        // 누적: 같은 노선에 쌓인 미처리 건수와, 같은 노선·유형의 최근 4주 반복(자기 자신 제외) 중 큰 쪽
        double routeRepeat = Math.min(100, (double) sameRouteCnt / REPEAT_FULL_CNT * 100);
        double typeRepeat = Math.min(100, Math.max(0, recentSameCnt - 1) / (double) (REPEAT_TYPE_FULL_CNT - 1) * 100);
        double repeat = Math.max(routeRepeat, typeRepeat);
        double scaleValue = scale.isPresent() ? Math.min(100, scale.getAsDouble()) : NEUTRAL_SCALE;   // 예측이 없으면 중간값
        boolean safety = URGENT.contains(r.getTypeName());
        int waitDays = (int) Math.max(0, waitDaysExact);
        boolean aged = !safety && waitDays >= AGING_DAYS;

        ReportPriorityDTO p = new ReportPriorityDTO();
        p.setReport(r);
        p.setUrgent(safety || aged);
        p.setAged(aged);
        p.setSeverityScore(severity);
        p.setScaleScore(scale.isPresent() ? round1(scaleValue) : null);
        p.setWaitScore(round1(wait));
        p.setRepeatScore(round1(repeat));
        p.setWaitDays(waitDays);
        p.setSameRouteCnt(sameRouteCnt);
        p.setRecentSameCnt(recentSameCnt);
        p.setScaleLabel(scale.isPresent() ? scaleLabelOf(scaleValue) : "예측 없음");
        p.setScore(round1(WEIGHT_SEVERITY * severity + WEIGHT_SCALE * scaleValue
            + WEIGHT_WAIT * wait + WEIGHT_REPEAT * repeat));
        p.setReasonList(reasons(p));
        return p;
    }

    // 관리자가 "왜 이 순서인지" 바로 알 수 있도록 눈에 띄는 항목만 문구로 만듦
    private List<String> reasons(ReportPriorityDTO p) {
        List<String> list = new ArrayList<>();
        if (p.isAged()) {
            list.add("장기 방치 승격");
        } else if (p.isUrgent()) {
            list.add("안전 관련 긴급");
        }
        if (p.getScaleScore() == null) {
            list.add("혼잡도 예측 없음");
        } else if (p.getScaleScore() >= 70) {
            list.add("앞으로 6시간 혼잡 예상 " + Math.round(p.getScaleScore()) + "%");
        }
        if (p.getWaitDays() >= 3) {
            list.add(p.getWaitDays() + "일 방치");
        }
        if (p.getSameRouteCnt() >= 2) {
            list.add("같은 노선 " + p.getSameRouteCnt() + "건");
        }
        if (p.getRecentSameCnt() >= 2) {
            list.add("같은 유형 최근 4주 " + p.getRecentSameCnt() + "건");
        }
        return list;
    }

    // 예측 혼잡도(%)를 이용 단계 문구로: 경계는 혼잡 등급(RouteForecastService)과 같은 값을 씀
    private String scaleLabelOf(double scale) {
        String grade = RouteForecastService.gradeOf(scale);
        return "HIGH".equals(grade) ? "많음" : ("MID".equals(grade) ? "보통" : "적음");
    }

    // ---- 신고 관리 목록 위쪽 요약 ----

    // 미처리 신고 수와 그중 긴급(안전 유형 또는 14일 이상 방치) 신고 수
    public record Summary(int openCnt, int urgentCnt) {}

    public Summary summarize(List<ReportPriorityDTO> queue) {
        int urgent = (int) queue.stream().filter(ReportPriorityDTO::isUrgent).count();
        return new Summary(queue.size(), urgent);
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // 화면 설명용: 점수식의 가중치
    public static String formulaText() {
        return String.format("심각도 %d%% + 이용자규모 %d%% + 방치 %d%% + 누적 %d%%",
            Math.round(WEIGHT_SEVERITY * 100), Math.round(WEIGHT_SCALE * 100),
            Math.round(WEIGHT_WAIT * 100), Math.round(WEIGHT_REPEAT * 100));
    }
}
