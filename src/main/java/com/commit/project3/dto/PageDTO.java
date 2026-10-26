package com.commit.project3.dto;

import lombok.Getter;

// 화면의 페이지 번호 표시에 쓰는 값을 계산해 두는 클래스
@Getter
public class PageDTO {
    private static final int PAGE_BLOCK_SIZE = 5;   // 한 번에 보여줄 페이지 번호 개수

    private int page;           // 현재 페이지
    private int totalCnt;       // 전체 데이터 개수
    private int totalPage;      // 전체 페이지 수
    private int startPage;      // 보여줄 첫 페이지 번호
    private int endPage;        // 보여줄 마지막 페이지 번호
    private boolean prev;       // 이전 블록이 있는지
    private boolean next;       // 다음 블록이 있는지

    public PageDTO(int page, int displayDataCnt, int totalCnt) {
        this.totalCnt = totalCnt;
        // 데이터가 0개여도 페이지는 1개로 봄
        this.totalPage = Math.max(1, (int) Math.ceil((double) totalCnt / displayDataCnt));
        // 현재 페이지가 1보다 작거나 마지막 페이지보다 크면 범위 안으로 맞춤
        this.page = Math.min(Math.max(page, 1), totalPage);

        // 예) 블록 크기 5, 현재 7페이지 → 6 ~ 10
        this.startPage = ((this.page - 1) / PAGE_BLOCK_SIZE) * PAGE_BLOCK_SIZE + 1;
        this.endPage = Math.min(startPage + PAGE_BLOCK_SIZE - 1, totalPage);
        this.prev = startPage > 1;
        this.next = endPage < totalPage;
    }
}
