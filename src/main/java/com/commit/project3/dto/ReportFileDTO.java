package com.commit.project3.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportFileDTO {
    private int fileNo;         // 파일 번호
    private int reportNo;       // 소속 신고 번호
    private String originName;  // 원본 파일명
    private String saveName;    // 서버에 저장된 파일명
    private String fileDesc;    // 사진 설명
    private int sortNo;         // 순서 (1~3)

    // 화면에서 사진 / 동영상 / 그 외를 나눠 보여주기 위한 구분 (th:if="${file.image}" 처럼 사용)
    public boolean isImage() {
        return hasExt(List.of(".jpg", ".bmp", ".gif", ".png"));
    }

    // 브라우저에서 바로 재생할 수 있는 동영상은 mp4만 해당
    public boolean isMp4() {
        return hasExt(List.of(".mp4"));
    }

    private boolean hasExt(List<String> extList) {
        if (saveName == null) {
            return false;
        }
        String lower = saveName.toLowerCase();
        for (String ext : extList) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
