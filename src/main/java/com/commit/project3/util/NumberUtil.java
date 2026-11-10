package com.commit.project3.util;

public class NumberUtil {

    // 숫자가 아니거나 비어 있으면 오류 대신 fallback 을 돌려줌 (주소창 값이나 외부 응답처럼 믿을 수 없는 문자열용)
    public static int toInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
