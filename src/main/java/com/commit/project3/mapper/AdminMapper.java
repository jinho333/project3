package com.commit.project3.mapper;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.dto.StatDTO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminMapper {

    // 신고 관리 목록: 전체 회원의 신고 (검색 조건 + 정렬 + 페이징)
    List<ReportDTO> selectAdminReportList(SearchDTO searchDTO);

    // 검색 조건에 맞는 전체 개수
    int selectAdminReportCount(SearchDTO searchDTO);

    // 신고 상세: 회원 제한 없이 번호만으로 조회 (없으면 null)
    ReportDTO getAdminReportDetail(int reportNo);

    // 처리 상태 변경 + 처리 결과 저장 (answer가 null이면 기존 답변 유지)
    int updateReportProcess(ReportDTO reportDTO);

    // 대시보드 통계
    int selectTotalCount();
    List<StatDTO> selectStatusStat();
    List<StatDTO> selectRouteStat();
    List<StatDTO> selectTypeStat();

    // 노선 관리
    List<RouteDTO> selectAdminRouteList(String keyword);
    int insertRoute(RouteDTO routeDTO);
    int updateRoute(RouteDTO routeDTO);
    int deleteRoute(String routeNo);
    int countReportByRoute(String routeNo);     // 이 노선으로 접수된 신고 수
}
