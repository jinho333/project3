package com.commit.project3.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// 지도에 보여 줄 노선 목록(JSON)을 돌려주는 서비스
// 지금은 static/data/route-list.json (임시 노선 목록)을 읽고,
// 나중에 DB 의 노선 정보로 바꿀 때는 이 클래스 안쪽만 고치면 됨
@Service
public class RouteListService {

    private static final String LIST_PATH = "static/data/route-list.json";

    public String findRouteListJson() {
        ClassPathResource resource = new ClassPathResource(LIST_PATH);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("노선 목록을 읽지 못했습니다.", e);
        }
    }
}
