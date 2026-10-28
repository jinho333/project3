package com.commit.project3.service;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.RouteDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.dto.StatDTO;
import com.commit.project3.mapper.AdminMapper;
import com.commit.project3.mapper.ReportMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private static final String ROUTE_REGEX = "^[A-Za-z0-9가-힣\\-]{1,20}$";    // 사용자 접수 화면과 같은 규칙
    private static final int MAX_ANSWER_LENGTH = 2000;
    private static final int MAX_ROUTE_NAME_LENGTH = 100;
    private static final List<String> STATUS_ORDER = List.of("RECEIVED", "PROCESSING", "DONE"); // 진행 순서

    private final AdminMapper adminMapper;
    private final ReportMapper reportMapper;    // 첨부파일 목록·유형 목록은 사용자 쪽 쿼리를 그대로 사용

    public AdminService(AdminMapper adminMapper, ReportMapper reportMapper) {
        this.adminMapper = adminMapper;
        this.reportMapper = reportMapper;
    }

    // ===== 신고 관리 =====

    public List<ReportDTO> getReportList(SearchDTO searchDTO) {
        return adminMapper.selectAdminReportList(searchDTO);
    }

    public int getReportCount(SearchDTO searchDTO) {
        return adminMapper.selectAdminReportCount(searchDTO);
    }

    // 신고 상세 (첨부파일 포함): 없는 번호면 null
    public ReportDTO getReportDetail(int reportNo) {
        ReportDTO reportDTO = adminMapper.getAdminReportDetail(reportNo);
        if (reportDTO != null) {
            reportDTO.setFileList(reportMapper.selectReportFileList(reportNo));
        }
        return reportDTO;
    }

    // 처리 상태 변경 + 처리 결과 저장: 성공하면 null, 실패하면 오류 문구
    // 상태는 접수 → 처리중 → 완료 방향으로만 바꿀 수 있고, 완료하려면 처리 결과가 있어야 함
    public String processReport(int reportNo, String status, String answer) {
        ReportDTO current = adminMapper.getAdminReportDetail(reportNo);
        if (current == null) {
            return "존재하지 않는 신고입니다.";
        }
        // List.of(...)는 null을 넣으면 오류가 나므로 null을 먼저 걸러냄
        if (status == null || !STATUS_ORDER.contains(status)) {
            return "처리 상태를 올바르게 선택해주세요.";
        }
        int newRank = STATUS_ORDER.indexOf(status);
        if (newRank < STATUS_ORDER.indexOf(current.getStatus())) {
            return "이미 진행된 상태로는 되돌릴 수 없습니다.";
        }

        // 빈 답변은 null로 바꿔 기존 답변을 유지
        answer = (answer == null || answer.trim().isEmpty()) ? null : answer.trim();
        if (answer != null && answer.length() > MAX_ANSWER_LENGTH) {
            return "처리 결과는 " + MAX_ANSWER_LENGTH + "자 이내로 입력해주세요.";
        }
        if ("DONE".equals(status) && answer == null && current.getAnswer() == null) {
            return "완료 처리하려면 처리 결과를 입력해주세요.";
        }

        ReportDTO reportDTO = new ReportDTO();
        reportDTO.setReportNo(reportNo);
        reportDTO.setStatus(status);
        reportDTO.setAnswer(answer);
        adminMapper.updateReportProcess(reportDTO);
        return null;
    }

    // ===== 대시보드 =====

    public int getTotalCount() {
        return adminMapper.selectTotalCount();
    }

    public List<StatDTO> getStatusStat() {
        return adminMapper.selectStatusStat();
    }

    public List<StatDTO> getRouteStat() {
        return adminMapper.selectRouteStat();
    }

    public List<StatDTO> getTypeStat() {
        return adminMapper.selectTypeStat();
    }

    // ===== 노선 관리 =====

    public List<RouteDTO> getRouteList(String keyword) {
        return adminMapper.selectAdminRouteList(keyword == null ? "" : keyword.trim());
    }

    // 노선 등록: 성공하면 null, 실패하면 오류 문구
    public String addRoute(RouteDTO routeDTO) {
        routeDTO.setRouteNo(trim(routeDTO.getRouteNo()));
        routeDTO.setRouteName(trim(routeDTO.getRouteName()));

        String error = checkRoute(routeDTO);
        if (error != null) {
            return error;
        }
        try {
            adminMapper.insertRoute(routeDTO);
        } catch (DuplicateKeyException e) {
            // 노선번호는 PRIMARY KEY라 동시에 같은 번호가 들어와도 DB가 막아 줌
            return "이미 등록된 노선번호입니다.";
        }
        return null;
    }

    // 노선 이름 수정 (노선번호는 신고와 연결되어 있어 바꾸지 않음)
    public String modifyRoute(RouteDTO routeDTO) {
        routeDTO.setRouteNo(trim(routeDTO.getRouteNo()));
        routeDTO.setRouteName(trim(routeDTO.getRouteName()));

        String error = checkRoute(routeDTO);
        if (error != null) {
            return error;
        }
        if (adminMapper.updateRoute(routeDTO) == 0) {
            return "존재하지 않는 노선입니다.";
        }
        return null;
    }

    // 노선 삭제: 이미 신고가 접수된 노선은 신고 데이터가 노선과 끊어지므로 막음
    public String removeRoute(String routeNo) {
        routeNo = trim(routeNo);
        if (routeNo == null || routeNo.isEmpty()) {
            return "삭제할 노선을 선택해주세요.";
        }
        if (adminMapper.countReportByRoute(routeNo) > 0) {
            return "신고가 접수된 노선은 삭제할 수 없습니다.";
        }
        if (adminMapper.deleteRoute(routeNo) == 0) {
            return "존재하지 않는 노선입니다.";
        }
        return null;
    }

    // 노선번호·이름 형식 검사
    private String checkRoute(RouteDTO routeDTO) {
        if (routeDTO.getRouteNo() == null || !routeDTO.getRouteNo().matches(ROUTE_REGEX)) {
            return "노선번호는 영문, 숫자, 한글, - 로 20자 이내로 입력해주세요. (예: 3600)";
        }
        if (routeDTO.getRouteName() == null || routeDTO.getRouteName().isEmpty()
                || routeDTO.getRouteName().length() > MAX_ROUTE_NAME_LENGTH) {
            return "노선 이름은 1~" + MAX_ROUTE_NAME_LENGTH + "자로 입력해주세요.";
        }
        return null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
