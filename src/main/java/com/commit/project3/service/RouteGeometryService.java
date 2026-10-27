package com.commit.project3.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

// 노선번호로 노선 선(GeoJSON)을 찾아주는 서비스
// 지금은 static/data/routes/{노선번호}.geojson 파일에서 읽고, 나중에 DB 조회로 바꿀 때는 이 클래스 안쪽만 고치면 됨
@Service
public class RouteGeometryService {

    private static final String BASE_PATH = "static/data/routes/";

    // 허용하는 노선번호: 숫자, 영문, 한글 1~10자 (../ 같은 입력으로 다른 파일을 읽지 못하게 막음)
    private static final Pattern ROUTE_NO_PATTERN = Pattern.compile("^[0-9A-Za-z가-힣]{1,10}$");

    public boolean isValidRouteNo(String routeNo) {
        return routeNo != null && ROUTE_NO_PATTERN.matcher(routeNo).matches();
    }

    // 노선이 있으면 GeoJSON 문자열, 없으면 비어 있는 Optional
    public Optional<String> findGeoJson(String routeNo) {
        if (!isValidRouteNo(routeNo)) {
            return Optional.empty();
        }
        ClassPathResource resource = new ClassPathResource(BASE_PATH + routeNo + ".geojson");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("노선 데이터를 읽지 못했습니다: " + routeNo, e);
        }
    }
}
