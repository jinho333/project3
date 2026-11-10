package com.commit.project3.service;

import com.commit.project3.service.RouteFacts.Run;
import com.commit.project3.service.RouteFacts.Stats;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 노선 분석에서 서버가 직접 쓰는 사실 문장 (AI 가 쓰지 않으므로 숫자와 고정 문구가 틀리거나 빠지지 않음)
final class RouteFactText {

    static final int FEW_REPORTS = 5;                     // 미완료 신고가 이보다 적으면 우연일 수 있다고 덧붙임
    static final int AVG_DAYS = 7;                        // 평균을 내고 날짜 묶음을 만드는 날 수 (오늘부터)
    static final int MAX_RUNS = 4;                        // 값이 같은 연속된 날의 묶음이 이 개수 이하면 묶음으로 말하고, 더 많으면 평균과 가장 많은/적은 날로 말함
    static final double BIG_GAP_RATIO = 3.0;              // 두 노선 이용자 수가 이 배수 이상 차이 나면 비교 주의 안내
    static final String NO_FORECAST_NOTICE = "이용자 수 예측이 없습니다.";

    private RouteFactText() {}

    // ---- 서버가 직접 쓰는 사실 문장 ----

    // 노선 하나의 1번 줄: 미완료 건수와 가장 오래된 신고
    static String factOpen(RouteFacts f) {
        int total = f.total();
        if (total == 0) {
            return "미완료 신고가 없습니다.";
        }
        StringBuilder sb = new StringBuilder("미완료 ").append(total).append("건(접수 ").append(f.open().getReceivedCnt())
            .append("건, 처리중 ").append(f.open().getProcessingCnt()).append("건)");
        if (f.oldestDays() != null) {
            sb.append(f.oldestDays() == 0 ? ", 가장 오래된 신고는 오늘 접수되었습니다." : ", 가장 오래된 신고는 " + f.oldestDays() + "일째입니다.");
        } else {
            sb.append("입니다.");
        }
        if (total < FEW_REPORTS) {
            sb.append(" 건수가 적어 우연일 수 있습니다.");
        }
        return sb.toString();
    }

    // 노선 하나의 3번 줄 숫자 문장: 값이 같은 연속된 날을 날짜 범위로 묶어서 말함
    // 예) 오늘부터 7일 예상 이용자는 11/9(월)~11/13(금) 하루 약 9,713명, 11/14(토)~11/15(일) 하루 약 8,254명입니다. ...
    // 묶음이 MAX_RUNS 개를 넘으면(날마다 값이 다른 예측) 하루 평균과 가장 많은/적은 날로 말함
    static String factForecast(RouteFacts f) {
        Stats s = f.stats();
        if (s == null) {
            return f.ridership().message() != null ? f.ridership().message() : NO_FORECAST_NOTICE;
        }
        int days = Math.min(AVG_DAYS, s.dayCount());
        if (s.runs().size() > MAX_RUNS) {
            return new StringBuilder("오늘부터 ").append(days)
                .append("일 하루 평균 예상 이용자는 약 ").append(fmt(s.avg7())).append("명입니다. 앞으로 ").append(s.dayCount())
                .append("일 중 가장 많은 날은 ").append(dateText(s.max())).append(" ").append(fmt(s.max().predicted()))
                .append("명, 가장 적은 날은 ").append(dateText(s.min())).append(" ").append(fmt(s.min().predicted())).append("명입니다.").toString();
        }
        StringBuilder sb = new StringBuilder("오늘부터 ").append(days).append("일 예상 이용자는 ").append(runsText(s)).append("입니다.");
        if (s.runs().size() == 2 && s.runs().get(0).value() != s.runs().get(1).value()) {
            Run hi = s.runs().get(0).value() > s.runs().get(1).value() ? s.runs().get(0) : s.runs().get(1);
            Run lo = hi == s.runs().get(0) ? s.runs().get(1) : s.runs().get(0);
            long pct = Math.round((double) (hi.value() - lo.value()) / hi.value() * 100);
            sb.append(" 값이 낮은 구간(").append(rangeText(lo)).append(")은 높은 구간보다 약 ").append(pct).append("% 적습니다.");
        }
        return sb.toString();
    }

    // "11/9(월)~11/13(금) 하루 약 9,713명, 11/14(토)~11/15(일) 하루 약 8,254명"
    static String runsText(Stats s) {
        List<String> parts = new ArrayList<>();
        for (Run r : s.runs()) {
            parts.add(rangeText(r) + " 하루 약 " + fmt(r.value()) + "명");
        }
        return String.join(", ", parts);
    }

    // 하루면 "11/9(월)", 여러 날이면 "11/9(월)~11/13(금)"
    static String rangeText(Run r) {
        return r.first() == r.last() ? dateText(r.first()) : dateText(r.first()) + "~" + dateText(r.last());
    }

    // 두 노선의 1번 줄: 선택 노선, 비교 노선 순서
    static String factOpenCompare(RouteFacts a, RouteFacts b) {
        String text = openShort(a) + ", " + openShort(b) + ".";
        if (Math.min(a.total(), b.total()) < FEW_REPORTS) {
            text += " 건수가 적은 노선은 우연일 수 있습니다.";
        }
        return text;
    }

    static String openShort(RouteFacts f) {
        if (f.total() == 0) {
            return f.routeNo() + "번 없음";
        }
        return f.routeNo() + "번 " + f.total() + "건" + (f.oldestDays() != null ? "(가장 오래된 신고 "
            + (f.oldestDays() == 0 ? "오늘" : f.oldestDays() + "일째") + ")" : "");
    }

    // 두 노선의 2번 줄: 오늘부터 7일 하루 평균 비교, 규모 차이가 크면 직접 비교 주의 안내
    static String factForecastCompare(RouteFacts a, RouteFacts b) {
        Stats sa = a.stats(), sb = b.stats();
        if (sa == null || sb == null) {
            StringBuilder out = new StringBuilder();
            for (RouteFacts f : List.of(a, b)) {
                if (f.stats() == null) {
                    out.append(f.routeNo()).append("번은 ").append(f.ridership().message() != null
                        ? f.ridership().message() : NO_FORECAST_NOTICE).append(' ');
                }
            }
            if (sa != null || sb != null) {
                RouteFacts ok = sa != null ? a : b;
                out.append(ok.routeNo()).append("번은 오늘부터 ").append(Math.min(AVG_DAYS, ok.stats().dayCount()))
                    .append("일 하루 평균 약 ").append(fmt(ok.stats().avg7())).append("명입니다.");
            }
            return out.toString().trim();
        }
        StringBuilder out = new StringBuilder("오늘부터 ").append(Math.min(AVG_DAYS, Math.min(sa.dayCount(), sb.dayCount())))
            .append("일 하루 평균 ").append(a.routeNo()).append("번 약 ").append(fmt(sa.avg7())).append("명, ")
            .append(b.routeNo()).append("번 약 ").append(fmt(sb.avg7())).append("명입니다.");
        RouteFacts big = sa.avg7() >= sb.avg7() ? a : b;
        int hi = Math.max(sa.avg7(), sb.avg7()), lo = Math.min(sa.avg7(), sb.avg7());
        if (lo > 0 && hi != lo) {
            double ratio = (double) hi / lo;
            out.append(' ').append(big.routeNo()).append("번이 ")
                .append(ratio < 2 ? "약 " + Math.round((ratio - 1) * 100) + "% 많습니다." : "약 " + String.format("%.1f", ratio) + "배입니다.");
            if (ratio >= BIG_GAP_RATIO) {
                out.append(" 두 노선의 이용자 규모 차이가 커서 신고 건수를 직접 비교할 때는 주의하세요.");
            }
        }
        return out.toString();
    }

    static String fmt(int v) {
        return String.format("%,d", v);
    }

    // 11/8(토)
    static String dateText(RidershipForecastService.Day d) {
        LocalDate date = LocalDate.parse(d.date());
        return date.getMonthValue() + "/" + date.getDayOfMonth() + "(" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
    }
}
