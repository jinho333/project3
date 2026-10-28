package com.commit.project3.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 노선의 한 방향이 실제 도로를 따라가는 선(좌표 목록)을 카카오 대중교통 경로 조회 API 로 그때그때 받아 오는 서비스
//  - 방향의 기점 정류장 -> 종점 정류장으로 조회하고, 결과 중 "환승 없이 이 노선 하나로 가는 경로"를 고름
//  - 카카오 정책상 좌표를 저장할 수 없으므로 DB, 파일, 서버 메모리에 저장(캐시)하지 않고 호출할 때마다 새로 받아 그대로 돌려줌
//  - 쓸 수 없는 경우(키 없음, 순환 노선, 직행 경로 없음, 우리 정류장과 맞지 않음 등)는 이유(Reason)와 함께 비어 있는 결과를 돌려주고,
//    화면은 그때 정류장을 직선으로 이은 선을 그대로 씀
@Service
public class RoadRouteService {

    private static final Logger log = LoggerFactory.getLogger(RoadRouteService.class);

    private static final String KAKAO_BASE_URL = "https://dapi.kakao.com";
    private static final String KAKAO_PATH = "/v2/routing/publictraffic";

    // 기점과 종점이 이 거리(m)보다 가까우면 순환 노선으로 보고 조회하지 않음 (카카오가 결과를 주지 않아 호출만 낭비됨)
    private static final double LOOP_ENDS_WITHIN_M = 50;
    // 우리 정류장이 카카오 경로에서 이 거리(m)보다 멀면 그 정류장은 다른 운행 경로라고 봄 (시험에서 정상은 최대 60m 안쪽)
    private static final double MAX_STOP_DEVIATION_M = 300;
    // 일부 정류장만 어긋날 때, 맞는 정류장이 이 개수 이상이고 전체의 절반 이상 이어져 있으면 그 구간만 도로 선으로 씀
    private static final int MIN_PARTIAL_STOPS = 5;

    // 도로 선을 못 쓰는 이유 (화면은 어떤 이유든 직선으로 대체)
    public enum Reason {
        NO_KEY,               // REST 키 설정이 없음
        ROUTE_NOT_FOUND,      // 노선 데이터가 없음
        DIRECTION_NOT_FOUND,  // 그 방향이 없거나 정류장이 2개 미만
        LOOP,                 // 순환 노선 (기점과 종점이 같음)
        NO_RESULTS,           // 카카오가 경로를 찾지 못함
        NO_DIRECT,            // 환승 없는 직행 경로가 없음
        MISMATCH,             // 직행 경로가 있지만 우리 정류장과 맞지 않음
        KAKAO_ERROR           // 카카오 호출 실패 (네트워크, 한도 초과, 키 오류 등)
    }

    // points = [[경도, 위도], ...], 못 쓰는 경우에는 points 가 비어 있고 reason 이 채워짐
    // partial = true 이면 카카오 경로와 맞지 않는 끝부분은 정류장을 이은 직선으로 채운 선
    public record Result(List<double[]> points, boolean partial, Reason reason) {
        static Result ok(List<double[]> points, boolean partial) { return new Result(points, partial, null); }
        static Result fail(Reason reason) { return new Result(null, false, reason); }
        public boolean isOk() { return points != null; }
    }

    private record Stop(String name, double lng, double lat) {}

    // 정류장마다: 카카오 경로 선까지의 최단거리(m)와, 그 지점이 놓인 선분 번호
    private record Fit(double[] dist, int[] seg) {
        int badCount() {
            int n = 0;
            for (double d : dist) if (d > MAX_STOP_DEVIATION_M) n++;
            return n;
        }
        double maxDist() {
            double m = 0;
            for (double d : dist) m = Math.max(m, d);
            return m;
        }
    }

    private final RouteGeometryService routeGeometryService;
    private final ObjectMapper objectMapper;
    private final RestClient kakaoClient;
    private final String restKey;

    public RoadRouteService(RouteGeometryService routeGeometryService, ObjectMapper objectMapper,
                            @Value("${kakao.rest.key:}") String restKey) {
        this.routeGeometryService = routeGeometryService;
        this.objectMapper = objectMapper;
        this.restKey = restKey == null ? "" : restKey.trim();

        // 카카오가 느려도 화면이 오래 멈추지 않게 시간 제한을 둠. py.base.url 용 RestClient 빈과는 따로 만듦
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.kakaoClient = RestClient.builder().baseUrl(KAKAO_BASE_URL).requestFactory(factory).build();
    }

    public Result findRoadPath(String routeNo, int brtId) {
        if (restKey.isEmpty()) {
            return Result.fail(Reason.NO_KEY);
        }
        Optional<List<Stop>> found = loadStops(routeNo, brtId);
        if (found.isEmpty()) {
            return Result.fail(Reason.DIRECTION_NOT_FOUND);
        }
        List<Stop> stops = found.get();
        Stop start = stops.get(0), end = stops.get(stops.size() - 1);
        if (distanceM(start.lng, start.lat, end.lng, end.lat) < LOOP_ENDS_WITHIN_M) {
            return Result.fail(Reason.LOOP);
        }

        JsonNode body;
        try {
            body = kakaoClient.get()
                .uri(uri -> uri.path(KAKAO_PATH)
                    .queryParam("start_x", start.lng).queryParam("start_y", start.lat).queryParam("s_name", start.name)
                    .queryParam("end_x", end.lng).queryParam("end_y", end.lat).queryParam("e_name", end.name)
                    .build())
                .header("Authorization", "KakaoAK " + restKey)   // 키는 로그나 응답에 남기지 않음
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("카카오 경로 조회 실패 (노선 {}, 방향 {}): {}", routeNo, brtId, e.getClass().getSimpleName());
            return Result.fail(Reason.KAKAO_ERROR);
        }
        if (body == null || !"OK".equals(body.path("status").asString(""))) {
            return Result.fail(Reason.NO_RESULTS);
        }

        // 환승 없이 이 노선 하나로 가는 경로들 중, 우리 정류장과 가장 잘 맞는 것(어긋나는 정류장이 가장 적은 것)을 고름
        List<double[]> best = null;
        Fit bestFit = null;
        boolean anyDirect = false;
        for (JsonNode route : body.path("routes")) {
            List<double[]> points = directPoints(route, routeNo);
            if (points == null) continue;
            anyDirect = true;
            Fit fit = fitStops(stops, points);
            if (bestFit == null || fit.badCount() < bestFit.badCount()
                    || (fit.badCount() == bestFit.badCount() && fit.maxDist() < bestFit.maxDist())) {
                bestFit = fit;
                best = points;
            }
        }
        if (!anyDirect) {
            return Result.fail(body.path("routes").isEmpty() ? Reason.NO_RESULTS : Reason.NO_DIRECT);
        }
        if (bestFit.badCount() == 0) {
            return Result.ok(best, false);
        }
        return partialPath(routeNo, brtId, stops, best, bestFit);
    }

    // 일부 정류장만 카카오 경로와 어긋날 때: 맞는 정류장이 가장 길게 이어진 구간만 카카오 선을 쓰고,
    // 그 앞뒤의 어긋나는 정류장은 정류장을 이은 직선으로 채움 (구간이 너무 짧으면 쓰지 않음)
    private Result partialPath(String routeNo, int brtId, List<Stop> stops, List<double[]> path, Fit fit) {
        int bestStart = -1, bestLen = 0, runStart = -1;
        for (int i = 0; i <= stops.size(); i++) {
            boolean good = i < stops.size() && fit.dist()[i] <= MAX_STOP_DEVIATION_M;
            if (good && runStart < 0) runStart = i;
            if (!good && runStart >= 0) {
                if (i - runStart > bestLen) { bestStart = runStart; bestLen = i - runStart; }
                runStart = -1;
            }
        }
        int a = bestStart, b = bestStart + bestLen - 1;
        if (bestLen < MIN_PARTIAL_STOPS || bestLen * 2 < stops.size() || fit.seg()[a] > fit.seg()[b]) {
            log.info("카카오 경로가 정류장과 맞지 않아 쓰지 않음 (노선 {}, 방향 {}, 맞는 정류장 {}/{}개)",
                routeNo, brtId, bestLen, stops.size());
            return Result.fail(Reason.MISMATCH);
        }

        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < a; i++) out.add(new double[] { stops.get(i).lng, stops.get(i).lat });
        for (int v = fit.seg()[a]; v <= Math.min(fit.seg()[b] + 1, path.size() - 1); v++) out.add(path.get(v));
        for (int i = b + 1; i < stops.size(); i++) out.add(new double[] { stops.get(i).lng, stops.get(i).lat });
        log.info("카카오 경로를 일부만 사용 (노선 {}, 방향 {}, 정류장 {}~{}번 / 전체 {}개)", routeNo, brtId, a, b, stops.size());
        return Result.ok(out, true);
    }

    // 경로가 "환승 0회, 구간 1개, 그 구간이 우리 노선 버스"이면 그 구간의 좌표 목록, 아니면 null
    private List<double[]> directPoints(JsonNode route, String routeNo) {
        if (route.path("properties").path("transfers").asInt(-1) != 0) return null;
        JsonNode steps = route.path("steps");
        if (steps.size() != 1) return null;
        JsonNode step = steps.get(0);
        if (!"BUS".equals(step.path("properties").path("type").asString(""))) return null;

        boolean ours = false;
        for (JsonNode vehicle : step.path("properties").path("vehicles")) {
            if (routeNo.equals(vehicle.path("name").asString("").replace("번", "").trim())) ours = true;
        }
        if (!ours) return null;

        List<double[]> points = new ArrayList<>();
        for (JsonNode p : step.path("path").path("points")) {
            if (p.size() >= 2) points.add(new double[] { p.get(0).asDouble(), p.get(1).asDouble() });
        }
        return points.size() >= 2 ? points : null;
    }

    // 방향의 정류장을 순서대로 읽음. 정류장이 2개 미만이면 비어 있는 Optional
    private Optional<List<Stop>> loadStops(String routeNo, int brtId) {
        Optional<String> geoJson = routeGeometryService.findGeoJson(routeNo);
        if (geoJson.isEmpty()) return Optional.empty();

        List<double[]> ordered = new ArrayList<>();       // [순번, 경도, 위도]
        List<String> names = new ArrayList<>();
        for (JsonNode f : objectMapper.readTree(geoJson.get()).path("features")) {
            JsonNode props = f.path("properties");
            if (!"stop".equals(props.path("kind").asString("")) || props.path("brt_id").asInt(-1) != brtId) continue;
            JsonNode c = f.path("geometry").path("coordinates");
            ordered.add(new double[] { props.path("order").asInt(), c.get(0).asDouble(), c.get(1).asDouble() });
            names.add(props.path("name").asString(""));
        }
        if (ordered.size() < 2) return Optional.empty();

        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) index.add(i);
        index.sort((a, b) -> Double.compare(ordered.get(a)[0], ordered.get(b)[0]));
        List<Stop> stops = new ArrayList<>();
        for (int i : index) stops.add(new Stop(names.get(i), ordered.get(i)[1], ordered.get(i)[2]));
        return Optional.of(stops);
    }

    // 정류장마다 경로 선까지의 최단거리(m)와 그 지점이 놓인 선분 번호를 구함
    private Fit fitStops(List<Stop> stops, List<double[]> path) {
        double[] dist = new double[stops.size()];
        int[] seg = new int[stops.size()];
        for (int k = 0; k < stops.size(); k++) {
            double nearest = Double.MAX_VALUE;
            int nearestSeg = 0;
            for (int i = 0; i + 1 < path.size(); i++) {
                double d = distanceToSegmentM(stops.get(k), path.get(i), path.get(i + 1));
                if (d < nearest) { nearest = d; nearestSeg = i; }
            }
            dist[k] = nearest;
            seg[k] = nearestSeg;
        }
        return new Fit(dist, seg);
    }

    // 점(정류장)에서 선분까지의 거리(m): 울산 정도의 좁은 범위라 위도 기준으로 평면에 편 값으로 계산
    private double distanceToSegmentM(Stop s, double[] a, double[] b) {
        double kx = Math.cos(Math.toRadians(s.lat)) * 111320, ky = 110540;
        double ax = (a[0] - s.lng) * kx, ay = (a[1] - s.lat) * ky;
        double bx = (b[0] - s.lng) * kx, by = (b[1] - s.lat) * ky;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        return Math.hypot(ax + t * dx, ay + t * dy);
    }

    private double distanceM(double lng1, double lat1, double lng2, double lat2) {
        double kx = Math.cos(Math.toRadians((lat1 + lat2) / 2)) * 111320, ky = 110540;
        return Math.hypot((lng2 - lng1) * kx, (lat2 - lat1) * ky);
    }
}
