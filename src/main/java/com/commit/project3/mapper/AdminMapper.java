package com.commit.project3.mapper;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.dto.RouteOpenCountDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.dto.StatDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminMapper {

    // 신고 관리 목록: 전체 회원의 신고 (검색 조건 + 정렬 + 페이징)
    List<ReportDTO> selectAdminReportList(SearchDTO searchDTO);

    // 검색 조건에 맞는 전체 개수
    int selectAdminReportCount(SearchDTO searchDTO);

    // 우선순위 계산용: 아직 끝나지 않은(접수 + 처리중) 모든 신고 (페이징 없음)
    List<ReportDTO> selectOpenReportAll();

    // 최근 4주 노선·유형별 신고 수 (LABEL = "노선번호|유형번호"): 반복 신고인지 판단하는 데 씀
    List<StatDTO> selectRecentRouteTypeCount();

    // 신고 상세: 회원 제한 없이 번호만으로 조회 (없으면 null)
    ReportDTO getAdminReportDetail(int reportNo);

    // 처리 상태 변경 + 처리 결과 저장 (answer가 null이면 기존 답변 유지)
    int updateReportProcess(ReportDTO reportDTO);

    // 대시보드 통계
    int selectPeriodCount(@Param("days") int days);             // 어제까지 최근 days일의 신고 수
    int selectDoneCount(@Param("days") int days);               // 어제까지 최근 days일에 처리 완료된 수
    List<StatDTO> selectStatusStat();                            // 접수·처리중: 현재 남은 건수 (기간 없음)
    List<StatDTO> selectRouteStat(@Param("days") int days);
    List<StatDTO> selectTypeStat(@Param("days") int days);
    List<StatDTO> selectDailyStat(@Param("days") int days);      // 날짜별 접수 건수 (AI 분석용)
    List<ReportDTO> selectRecentReportText(@Param("days") int days, @Param("limit") int limit);   // 신고 내용 샘플 (AI 분석용)

    // 지도(버스 혼잡도): 노선별 아직 끝나지 않은 신고 수 (routeNo 가 null 이면 신고가 남은 모든 노선)
    List<RouteOpenCountDTO> selectOpenCountByRoute(@Param("routeNo") String routeNo);

    // 지도 AI 분석용: 노선 하나의 미처리 신고 (가장 오래된 신고가 며칠째인지 / 유형별 건수 / 내용 샘플)
    Integer selectOpenOldestDays(@Param("routeNo") String routeNo);     // 미처리 신고가 없으면 null
    List<StatDTO> selectOpenTypeStat(@Param("routeNo") String routeNo);
    List<ReportDTO> selectOpenReportText(@Param("routeNo") String routeNo, @Param("limit") int limit);

    // 노선 관리
    List<RouteDTO> selectAdminRouteList(String keyword);
    int insertRoute(RouteDTO routeDTO);
    int updateRoute(RouteDTO routeDTO);
    int deleteRoute(String routeNo);
    // 노선번호 목록에 해당하는 노선의 이름 (지도 노선 목록용, DB 에 없는 번호는 결과에 없음)
    List<RouteDTO> selectRouteNames(@Param("routeNoList") List<String> routeNoList);
    int countReportByRoute(String routeNo);     // 이 노선으로 접수된 신고 수
}
