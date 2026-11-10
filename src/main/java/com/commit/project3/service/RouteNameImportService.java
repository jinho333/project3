package com.commit.project3.service;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.commit.project3.mapper.AdminMapper;
import com.commit.project3.util.NumberUtil;

// 울산광역시 BIS 정보(공공데이터포털 "울산광역시_BIS 정보_GW")의 노선정보 조회로 노선 이름을 받아 BUS_ROUTE 에서 "이름 미등록" 인 노선의 이름만 채움
// 이미 이름이 있는 노선은 바꾸지 않음. 인증키(ulsan.bis.key)는 git 에 올라가지 않는 application.yaml 에만 둠
// BIS 는 방향마다 한 줄씩 주므로(예: 114 는 2줄) 노선번호별로 모아 이름 하나로 만듦: 방향 이름이 3개 이상이면 맨 앞과 맨 뒤만 씀
@Service
public class RouteNameImportService {

    private static final String ENDPOINT = "https://apis.data.go.kr/6310000/bis/routeInfo";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 10;           // 무한 반복 방지
    private static final int NAME_MAX = 100;           // BUS_ROUTE.ROUTE_NAME 길이
    // BRTNAME 은 "114(대왕암공원 방면)" 모양: 괄호 안이 방향 이름
    private static final Pattern INSIDE_PAREN = Pattern.compile("^[^()]*\\((.*)\\)\\s*$");

    // 한 줄: 노선번호 / 노선명 / 방향(1 정, 2 역, 3 순환) / 구분(0 원노선, 1~ 지원노선)
    public record Row(String routeNo, String routeName, String direction, String routeClass) {}

    // ok: BIS 에서 목록을 받아 처리했는지. found: BIS 에서 이름을 만든 노선 수, filled: 실제로 이름을 채운 수
    public record Result(boolean ok, String message, int found, int filled) {
        static Result failed(String message) {
            return new Result(false, message, 0, 0);
        }
    }

    private final AdminMapper adminMapper;
    private final RestClient client;
    private final String serviceKey;

    public RouteNameImportService(AdminMapper adminMapper, @Value("${ulsan.bis.key:}") String serviceKey) {
        this.adminMapper = adminMapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Transactional
    public Result importNames() {
        if (serviceKey.isEmpty()) {
            return Result.failed("울산 BIS 인증키가 설정되지 않았습니다. application.yaml 의 ulsan.bis.key 를 확인하세요.");
        }
        List<Row> rows = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                Document doc = fetch(page);
                String root = doc.getDocumentElement().getTagName();
                if (!"tableInfo".equals(root)) {
                    // 인증키 오류 등은 OpenAPI_ServiceResponse 로 옴
                    System.err.println("[RouteName] BIS 오류 응답: " + root);
                    return Result.failed("BIS 가 노선 목록을 주지 않았습니다. 인증키가 이 서비스에 활용신청·승인된 키인지 확인하세요.");
                }
                String code = text(doc.getDocumentElement(), "resultCode");
                if (!"200".equals(code)) {
                    System.err.println("[RouteName] BIS resultCode=" + code);
                    return Result.failed("BIS 가 오류를 알려 왔습니다(" + text(doc.getDocumentElement(), "resultMsg") + ").");
                }
                NodeList list = doc.getElementsByTagName("row");
                for (int i = 0; i < list.getLength(); i++) {
                    Element e = (Element) list.item(i);
                    rows.add(new Row(text(e, "BRTNO"), text(e, "BRTNAME"), text(e, "DIRECTION"), text(e, "CLASS")));
                }
                int total = NumberUtil.toInt(text(doc.getDocumentElement(), "totalCnt"), 0);
                if (list.getLength() == 0 || rows.size() >= total) {
                    break;
                }
            }
        } catch (RestClientResponseException e) {
            System.err.println("[RouteName] 호출 실패: HTTP " + e.getStatusCode().value());
            return Result.failed("BIS 서버가 요청을 거절했습니다(HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            System.err.println("[RouteName] 연결 실패: " + e.getClass().getSimpleName());
            return Result.failed("BIS 서버에 연결하지 못했습니다. 잠시 후 다시 시도하세요.");
        } catch (Exception e) {
            System.err.println("[RouteName] 응답 해석 실패: " + e.getClass().getSimpleName());
            return Result.failed("BIS 응답을 읽지 못했습니다.");
        }

        Map<String, String> nameByRoute = buildNames(rows);
        if (nameByRoute.isEmpty()) {
            return Result.failed("BIS 에서 받은 노선이 없습니다.");
        }
        try {
            int filled = 0;
            for (Map.Entry<String, String> e : nameByRoute.entrySet()) {
                filled += adminMapper.updateRouteNameIfUnnamed(e.getKey(), e.getValue(), ForecastRouteSyncService.UNNAMED);
            }
            String msg = filled == 0
                ? "BIS 노선 " + nameByRoute.size() + "개를 확인했고 이름을 채울 노선이 없습니다."
                : "BIS 노선 " + nameByRoute.size() + "개를 확인했고 \"" + ForecastRouteSyncService.UNNAMED + "\" 노선 " + filled + "개의 이름을 채웠습니다.";
            return new Result(true, msg, nameByRoute.size(), filled);
        } catch (DataAccessException e) {
            System.err.println("[RouteName] DB 저장 실패: " + e.getClass().getSimpleName());
            return Result.failed("노선 이름을 저장하지 못했습니다. DB 연결을 확인하세요.");
        }
    }

    private Document fetch(int page) throws Exception {
        // 한글이 물음표로 깨지지 않도록 UTF-8 을 요청 (이 헤더가 없으면 BIS 가 한글을 ? 로 보냄)
        byte[] body = client.get()
            .uri(ENDPOINT + "?serviceKey={key}&pageNo={page}&numOfRows={rows}", serviceKey, page, PAGE_SIZE)
            .header("Accept-Charset", "UTF-8")
            .header("Accept", "application/xml;charset=UTF-8")
            .retrieve()
            .body(byte[].class);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);   // 외부에서 온 XML 이므로 DTD(XXE) 차단
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(body == null ? new byte[0] : body));
    }

    // 노선번호별 이름 만들기 (순서 유지). 외부 값이라 길이를 제한하고, 이름을 못 만든 노선은 뺌
    static Map<String, String> buildNames(List<Row> rows) {
        Map<String, List<Row>> byRoute = new LinkedHashMap<>();
        for (Row r : rows) {
            if (!r.routeNo().isBlank()) {
                byRoute.computeIfAbsent(r.routeNo().trim(), k -> new ArrayList<>()).add(r);
            }
        }
        Map<String, String> names = new LinkedHashMap<>();
        for (Map.Entry<String, List<Row>> e : byRoute.entrySet()) {
            String name = buildName(e.getValue());
            if (name != null) {
                names.put(e.getKey(), name.length() > NAME_MAX ? name.substring(0, NAME_MAX) : name);
            }
        }
        return names;
    }

    // 한 노선의 이름: 방향 이름(기점→종점 순)을 중복 없이 모아서
    //  1개: "A 순환"(순환 노선) 또는 "A 방면" / 2개: "A ↔ B" / 3개 이상: 맨 앞과 맨 뒤만 "A ↔ B"
    static String buildName(List<Row> routeRows) {
        List<Row> sorted = new ArrayList<>(routeRows);
        sorted.sort(Comparator.comparing(Row::direction).thenComparing(Row::routeClass));

        LinkedHashSet<String> places = new LinkedHashSet<>();
        boolean circular = false;
        for (Row r : sorted) {
            String inside = inside(r.routeName());
            if (inside.isEmpty()) {
                continue;
            }
            if (inside.endsWith("순환")) {
                circular = true;
            }
            String place = inside.replaceAll("\\s*(방면|순환)$", "").trim();
            if (!place.isEmpty()) {
                places.add(place);
            }
        }
        if (places.isEmpty()) {
            return null;
        }
        List<String> list = new ArrayList<>(places);
        if (list.size() == 1) {
            return list.get(0) + (circular ? " 순환" : " 방면");
        }
        return list.get(0) + " ↔ " + list.get(list.size() - 1);
    }

    // "114(대왕암공원 방면)" → "대왕암공원 방면"
    private static String inside(String routeName) {
        if (routeName == null) {
            return "";
        }
        Matcher m = INSIDE_PAREN.matcher(routeName.trim());
        return m.matches() ? m.group(1).trim() : "";
    }

    private static String text(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 || list.item(0).getTextContent() == null ? "" : list.item(0).getTextContent().trim();
    }
}
