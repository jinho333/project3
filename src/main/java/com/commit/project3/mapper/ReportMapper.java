package com.commit.project3.mapper;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportFileDTO;
import com.commit.project3.dto.ReportTypeGroupDTO;
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
}
