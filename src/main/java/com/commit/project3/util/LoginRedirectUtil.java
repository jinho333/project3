package com.commit.project3.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

// 로그인이 필요한 화면에서 로그인 화면으로 보낼 때, 원래 가려던 주소를 함께 넘겼다가 로그인 후 돌려보내는 용도
public class LoginRedirectUtil {

    // 돌아갈 주소로 허용하는 모양: "/"로 시작하는 우리 사이트 안의 주소만 (//, 백슬래시, 중괄호, 따옴표 등은 불허)
    private static final Pattern SAFE_URL = Pattern.compile("^/(?!/)[A-Za-z0-9\\-._~/?&=%+:,@!*()가-힣]{0,300}$");

    // 비로그인 상태로 들어온 요청에서: 로그인 화면 주소를 만듦 (원래 주소는 returnUrl 로 담김)
    // 예) /report?typeNo=5  →  redirect:/member/login?needLogin=true&returnUrl=%2Freport%3FtypeNo%3D5
    public static String loginUrl(HttpServletRequest request) {
        String url = request.getRequestURI();
        if (request.getQueryString() != null) {
            url += "?" + request.getQueryString();
        }
        String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8);
        return "redirect:/member/login?needLogin=true&returnUrl=" + encoded;
    }

    // 화면에서 넘어온 returnUrl 이 안전한 주소이면 그대로, 아니면 null
    // 이 검사가 없으면 returnUrl=https://나쁜사이트 처럼 바꿔서 로그인 후 다른 사이트로 보낼 수 있음 (open redirect)
    public static String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || !SAFE_URL.matcher(returnUrl).matches()) {
            return null;
        }
        // 로그인·로그아웃·회원가입 화면으로 돌려보내면 반복되거나 바로 로그아웃되므로 제외
        if (returnUrl.startsWith("/member/")) {
            return null;
        }
        return returnUrl;
    }
}
