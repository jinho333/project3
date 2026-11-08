package com.commit.project3.dto;

import lombok.Data;

import java.util.List;

// 미처리 신고 하나의 처리 우선순위: 신고 + 항목별 점수(0~100) + 합계
@Data
public class ReportPriorityDTO {
    private ReportDTO report;           // 신고 내용
    private int rank;                   // 처리 순서 (1이 가장 먼저)
    private boolean urgent;             // 맨 앞(긴급) 구간: 안전 유형이거나 14일 이상 방치된 신고
    private boolean aged;               // 안전 유형이 아니지만 오래 방치되어 긴급 구간으로 올라온 신고
    private double score;               // 합계 점수 (소수 첫째 자리)

    private double severityScore;       // 심각도 S: 신고 유형으로 정함
    private Double scaleScore;          // 이용자규모 U: 앞으로 6시간 예측 혼잡도(%) 평균, 예측이 없으면 null
    private double waitScore;           // 방치 W: 접수 후 지난 시간
    private double repeatScore;         // 누적 N: 같은 노선에 쌓인 미처리 건수

    private int waitDays;               // 접수 후 지난 일수
    private int sameRouteCnt;           // 같은 노선의 미처리 신고 수 (자기 자신 포함)
    private int recentSameCnt;          // 최근 4주 같은 노선·같은 유형으로 접수된 신고 수 (자기 자신 포함, 완료된 것도 포함)
    private String scaleLabel;          // 앞으로 6시간 이용 단계: 많음 / 보통 / 적음 / 예측 없음
    private List<String> reasonList;    // 화면에 보여줄 근거 문구
}
