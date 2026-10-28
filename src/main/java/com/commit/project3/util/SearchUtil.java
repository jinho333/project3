package com.commit.project3.util;

import com.commit.project3.dto.SearchDTO;
import org.springframework.validation.BindingResult;

import java.util.List;

public class SearchUtil {

    private static final List<String> STATUS_LIST = List.of("RECEIVED", "PROCESSING", "DONE");
    private static final List<Integer> SIZE_LIST = List.of(10, 20, 50);     // 선택 가능한 한 페이지 개수

    // 주소창으로 들어온 검색 조건을 서버에서 한 번 더 정리하고, 화면에 보여줄 안내 문구를 돌려줌 (없으면 null)
    public static String normalize(SearchDTO searchDTO, BindingResult bindingResult) {
        // List.of(...)는 contains(null)에서 오류가 나므로 null을 먼저 걸러냄
        if (searchDTO.getStatus() == null || !STATUS_LIST.contains(searchDTO.getStatus())) {
            searchDTO.setStatus(null);
        }
        if (!SIZE_LIST.contains(searchDTO.getDisplayDataCnt())) {
            searchDTO.setDisplayDataCnt(10);
        }
        if (!"old".equals(searchDTO.getSort())) {
            searchDTO.setSort("new");
        }
        searchDTO.setKeyword(cleanText(searchDTO.getKeyword(), 50));
        searchDTO.setRouteNo(cleanText(searchDTO.getRouteNo(), 20));

        String searchMsg = null;
        if (bindingResult.hasErrors()) {
            searchMsg = "형식이 올바르지 않은 검색 조건은 적용하지 않았습니다.";
        }
        if (searchDTO.getStartDate() != null && searchDTO.getEndDate() != null
                && searchDTO.getStartDate().isAfter(searchDTO.getEndDate())) {
            searchDTO.setStartDate(null);
            searchDTO.setEndDate(null);
            searchMsg = "시작일이 종료일보다 늦어 날짜 조건은 적용하지 않았습니다.";
        }
        return searchMsg;
    }

    // 검색어 정리: 앞뒤 공백 제거, 비어 있으면 null, 너무 길면 자름
    public static String cleanText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
