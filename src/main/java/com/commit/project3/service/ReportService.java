package com.commit.project3.service;

import com.commit.project3.dto.ReportDTO;
import com.commit.project3.dto.ReportFileDTO;
import com.commit.project3.dto.ReportTypeGroupDTO;
import com.commit.project3.dto.SearchDTO;
import com.commit.project3.mapper.ReportMapper;
import com.commit.project3.util.UploadUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ReportService {

    // 화면(JS)의 정규식과 같은 규칙
    private static final String PHONE_REGEX = "^01[016789]\\d{7,8}$";           // 숫자만, 01x로 시작 10~11자리
    private static final String ROUTE_REGEX = "^[A-Za-z0-9가-힣\\-]{1,20}$";     // 노선번호 (예: 3600, M5443, 3600-1)

    // 첨부 가능한 확장자 (점 포함)
    private static final List<String> IMAGE_EXT = List.of(".jpg", ".bmp", ".gif", ".png");
    private static final List<String> VIDEO_EXT = List.of(".avi", ".wmv", ".mpg", ".mov", ".mp4");
    private static final int MAX_FILE_COUNT = 3;
    private static final long MAX_TOTAL_SIZE = 200L * 1024 * 1024; // 총 200MB

    private final ReportMapper reportMapper;
    private final UploadUtil uploadUtil;

    public ReportService(ReportMapper reportMapper, UploadUtil uploadUtil) {
        this.reportMapper = reportMapper;
        this.uploadUtil = uploadUtil;
    }

    // 유형선택 모달용 목록
    public List<ReportTypeGroupDTO> getTypeGroupList() {
        return reportMapper.selectTypeGroupList();
    }

    // 신고 목록 (검색 조건 + 페이징)
    public List<ReportDTO> getReportList(SearchDTO searchDTO) {
        return reportMapper.selectReportList(searchDTO);
    }

    // 검색 조건에 맞는 신고 전체 개수
    public int getReportCount(SearchDTO searchDTO) {
        return reportMapper.selectReportCount(searchDTO);
    }

    // 신고 상세 (첨부파일 포함): 본인 신고가 아니거나 없는 번호면 null
    public ReportDTO getReportDetail(int reportNo, int memberNo) {
        ReportDTO reportDTO = reportMapper.getReportDetail(reportNo, memberNo);
        if (reportDTO != null) {
            reportDTO.setFileList(reportMapper.selectReportFileList(reportNo));
        }
        return reportDTO;
    }

    // 신고 접수: 성공하면 null, 실패하면 오류 문구를 돌려줌
    // files, fileDescs는 사진 칸 순서대로 들어옴 (선택하지 않은 칸은 빈 파일)
    public String regReport(ReportDTO reportDTO, List<MultipartFile> files, List<String> fileDescs) {
        // 앞뒤 공백 제거
        reportDTO.setReporterName(trim(reportDTO.getReporterName()));
        reportDTO.setReporterPhone(trim(reportDTO.getReporterPhone()));
        reportDTO.setRouteNo(trim(reportDTO.getRouteNo()));
        reportDTO.setContent(trim(reportDTO.getContent()));

        // 서버 쪽 입력값 검사
        if (reportDTO.getReporterName() == null || reportDTO.getReporterName().isEmpty()) {
            return "이름을 입력해주세요.";
        }
        if (reportDTO.getReporterPhone() == null || !reportDTO.getReporterPhone().matches(PHONE_REGEX)) {
            return "휴대전화번호는 숫자만 입력해주세요. (예: 01012345678)";
        }
        if (reportDTO.getTypeNo() <= 0) {
            return "유형을 선택해주세요.";
        }
        if (reportDTO.getRouteNo() == null || !reportDTO.getRouteNo().matches(ROUTE_REGEX)) {
            return "노선번호를 올바르게 입력해주세요. (예: 3600)";
        }
        if (reportDTO.getContent() == null || reportDTO.getContent().isEmpty()) {
            return "내용을 입력해주세요.";
        }

        // 첨부파일 검사: 개수, 확장자, 동영상 개수, 총 용량
        String fileError = checkFiles(files, fileDescs);
        if (fileError != null) {
            return fileError;
        }

        // 신고 저장
        try {
            reportMapper.insertReport(reportDTO);
        } catch (DataIntegrityViolationException e) {
            // 없는 유형 번호처럼 외래키가 맞지 않는 경우
            return "선택한 유형이 올바르지 않습니다.";
        }

        // 파일 저장 후 파일 정보 등록 (사진 칸 순서를 sortNo로 저장)
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile file = files.get(i);
                if (file.isEmpty()) {
                    continue;
                }
                ReportFileDTO reportFileDTO = new ReportFileDTO();
                reportFileDTO.setReportNo(reportDTO.getReportNo());
                reportFileDTO.setOriginName(file.getOriginalFilename());
                reportFileDTO.setSaveName(uploadUtil.saveFile(file));
                reportFileDTO.setFileDesc(getDesc(fileDescs, i));
                reportFileDTO.setSortNo(i + 1);
                reportMapper.insertReportFile(reportFileDTO);
            }
        } catch (IOException e) {
            return "파일 저장 중 오류가 발생했습니다.";
        }
        return null;
    }

    // 첨부파일 검사: 문제가 없으면 null, 있으면 오류 문구
    private String checkFiles(List<MultipartFile> files, List<String> fileDescs) {
        int fileCount = 0;
        int videoCount = 0;
        long totalSize = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file.isEmpty()) {
                continue; // 선택하지 않은 칸
            }
            fileCount++;
            totalSize += file.getSize();

            String ext = UploadUtil.getExt(file.getOriginalFilename());
            if (VIDEO_EXT.contains(ext)) {
                videoCount++;
            } else if (!IMAGE_EXT.contains(ext)) {
                return "등록 가능한 파일은 jpg, bmp, gif, png, avi, wmv, mpg, mov, mp4 입니다.";
            }
            if (getDesc(fileDescs, i).length() > 200) {
                return "사진 설명은 200자 이내로 입력해주세요.";
            }
        }

        if (fileCount > MAX_FILE_COUNT) {
            return "첨부파일은 3개까지 가능합니다.";
        }
        if (videoCount > 1) {
            return "동영상은 1개만 첨부할 수 있습니다.";
        }
        if (totalSize > MAX_TOTAL_SIZE) {
            return "첨부파일은 총 200MB 이하로 첨부해주세요.";
        }
        return null;
    }

    // i번째 사진 설명 (없으면 빈 문자열)
    private String getDesc(List<String> fileDescs, int i) {
        if (fileDescs == null || i >= fileDescs.size() || fileDescs.get(i) == null) {
            return "";
        }
        return fileDescs.get(i).trim();
    }

    // null이면 그대로, 아니면 앞뒤 공백 제거
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
