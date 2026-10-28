package com.commit.project3.mapper;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportFileDTO;
import com.commit.project3.dto.ReportTypeGroupDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.dto.SearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper {

    // 유형선택 모달용: 대분류와 그 안의 유형 목록
    List<ReportTypeGroupDTO> selectTypeGroupList();

    // 신고 등록 (등록 후 reportNo에 생성된 번호가 담김)
    void insertReport(ReportDTO reportDTO);

    // 신고 첨부파일 등록
    void insertReportFile(ReportFileDTO reportFileDTO);

    // 신고 목록 (검색 조건 + 페이징)
    List<ReportDTO> selectReportList(SearchDTO searchDTO);

    // 검색 조건에 맞는 신고 전체 개수 (페이지 수 계산용)
    int selectReportCount(SearchDTO searchDTO);

    // 신고 상세: 신고 번호와 회원 번호가 모두 맞아야 조회됨 (없으면 null)
    // 파라미터가 2개라서 XML에서 이름으로 꺼낼 수 있도록 @Param으로 이름을 붙임
    ReportDTO getReportDetail(@Param("reportNo") int reportNo, @Param("memberNo") int memberNo);

    // 신고에 첨부된 파일 목록
    List<ReportFileDTO> selectReportFileList(int reportNo);

    // 노선 목록 (노선번호·이름 검색, 최대 50개)
    List<RouteDTO> selectRouteList(String keyword);

    // 존재하는 노선인지 확인 (1이면 있음)
    int countRoute(String routeNo);

    // 신고 수정: 본인 신고이면서 아직 '접수' 상태일 때만 바뀜 (바뀐 행 수를 돌려줌)
    int updateReport(ReportDTO reportDTO);

    // 신고 삭제: 본인 신고이면서 아직 '접수' 상태일 때만 지워짐 (첨부파일 정보는 DB가 함께 삭제)
    int deleteReport(@Param("reportNo") int reportNo, @Param("memberNo") int memberNo);
}
