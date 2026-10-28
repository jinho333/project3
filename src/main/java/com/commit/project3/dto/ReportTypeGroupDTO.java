package com.commit.project3.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReportTypeGroupDTO {
    private int groupNo;                    // 대분류 번호
    private String groupName;               // 대분류 이름
    private List<ReportTypeDTO> typeList;   // 대분류에 속한 유형 목록
}
