package com.commit.project3.dto;

import lombok.Data;

@Data
public class TypeRankDTO {
    private int typeNo;         // 유형 번호
    private String groupName;   // 대분류 이름
    private String typeName;    // 유형 이름
    private int cnt;            // 지금까지 접수된 신고 건수
}
