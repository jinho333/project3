package com.commit.project3.dto;

import lombok.Data;

@Data
public class ReportTypeDTO {
    private int typeNo;         // 유형 번호
    private int groupNo;        // 소속 대분류 번호
    private String typeName;    // 유형 이름
}
