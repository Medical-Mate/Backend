package com.jinryomate.backend.hospital.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.hospital.HospitalProperties;
import com.jinryomate.backend.hospital.dto.HospitalDtos.Hospital;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * 심평원 병원정보서비스 호출.
 *
 * <p><b>URI 를 직접 조립한다.</b> {@code UriComponentsBuilder} 나 {@code RestClient} 의
 * 파라미터 기능을 쓰면 <b>서비스 키를 다시 인코딩해서 조용히 0건이 온다</b> — 공공데이터포털 키는
 * 이미 퍼센트 인코딩돼 있기 때문이다({@code …tA%3D%3D}). 인증 오류도 안 나고
 * {@code resultMsg} 는 {@code NORMAL SERVICE} 라, 검색이 안 되는 게 아니라 결과가 없는 것처럼
 * 보인다. 실제로 겪었다.
 *
 * <p>그래서 <b>키만 인코딩하지 않고</b> 나머지 값만 {@link URLEncoder} 로 감싼다.
 */
@Slf4j
public class HttpHospitalSearchClient implements HospitalSearchClient {

    private static final String PATH = "/getHospBasisList";

    /** 심평원이 정상 응답에 싣는 코드. */
    private static final String OK = "00";

    /** 오류일 때 바뀌는 최상위 키. 정상 응답의 {@code response} 자리에 이게 온다. */
    private static final String ERROR_ROOT = "OpenAPI_ServiceResponse";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final HospitalProperties properties;

    public HttpHospitalSearchClient(RestClient restClient,
                                    ObjectMapper objectMapper,
                                    HospitalProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public HospitalSearchResponse search(String query, int page, int size) {
        URI uri = URI.create(properties.baseUrl() + PATH
                // 키는 이미 인코딩된 문자열이다. 건드리지 않는다.
                + "?serviceKey=" + properties.serviceKey()
                + "&_type=json"
                + "&pageNo=" + page
                + "&numOfRows=" + size
                + "&yadmNm=" + URLEncoder.encode(query, StandardCharsets.UTF_8));

        long started = System.currentTimeMillis();
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (Exception e) {
            // 응답 본문에 키가 되비쳐 올 수 있어 예외 메시지를 남기지 않는다.
            log.warn("병원 검색 호출 실패: {}", e.getClass().getSimpleName());
            throw unavailable();
        }

        HospitalSearchResponse result = parse(body);
        log.info("병원 검색 상류 {}ms results={} total={}",
                System.currentTimeMillis() - started, result.hospitals().size(), result.totalCount());
        return result;
    }

    /**
     * 응답을 푼다.
     *
     * <p><b>"결과 없음"과 "상류 실패"를 반드시 갈라야 한다.</b> 앱이 사용자에게 하는 말이
     * "검색 결과 없음"과 "잠시 뒤 다시"로 갈리는데, 실패를 빈 목록으로 내보내면 둘 다 전자가 된다.
     *
     * <p>두 모양이 실제로 이렇게 다르다.
     *
     * <pre>
     * 결과 없음 : {"response":{"header":{"resultCode":"00",…},"body":{"items":"","totalCount":0}}}
     * 오류      : {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":…,"returnReasonCode":"30"}}}
     * </pre>
     *
     * <p><b>오류는 최상위 키부터 다르다.</b> 그래서 {@code response} 가 없으면 그 자체가 실패다.
     * 이유는 로그에만 남긴다 — 키가 안 맞는지 할당량을 넘겼는지는 운영자가 알아야 하고,
     * 사용자에게는 "잠시 뒤 다시"면 충분하다.
     */
    private HospitalSearchResponse parse(String body) {
        if (body == null || body.isBlank()) {
            log.error("병원 검색 응답이 비어 있습니다.");
            throw unavailable();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            // XML 오류 페이지가 오는 경우가 있다. 게이트웨이가 앞에서 끊을 때다.
            log.error("병원 검색 응답이 JSON 이 아닙니다.");
            throw unavailable();
        }

        if (root.has(ERROR_ROOT)) {
            JsonNode header = root.path(ERROR_ROOT).path("cmmMsgHeader");
            // 예: SERVICE_KEY_IS_NOT_REGISTERED_ERROR(30) · LIMITED_NUMBER_OF_SERVICE_REQUESTS(22)
            log.error("병원 검색 상류 오류 code={} msg={}",
                    header.path("returnReasonCode").asText(), header.path("errMsg").asText());
            throw unavailable();
        }

        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asText();
        if (!OK.equals(code)) {
            log.error("병원 검색 오류 resultCode={} resultMsg={}",
                    code, response.path("header").path("resultMsg").asText());
            throw unavailable();
        }

        JsonNode responseBody = response.path("body");

        // 정상 응답이면 결과가 0건이어도 totalCount 가 있다. 없으면 성공 모양을 흉내 낸
        // 반쪽 응답이라, 0건으로 읽으면 실패를 "결과 없음"으로 감추게 된다.
        if (!responseBody.hasNonNull("totalCount")) {
            log.error("병원 검색 응답에 totalCount 가 없습니다. 상류가 정상 모양을 주지 않았습니다.");
            throw unavailable();
        }

        JsonNode items = responseBody.path("items").path("item");

        List<Hospital> hospitals = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(item -> hospitals.add(toHospital(item)));
        } else if (items.isObject()) {
            // 한 건이면 배열이 아니라 객체 하나로 온다.
            hospitals.add(toHospital(items));
        }

        return new HospitalSearchResponse(List.copyOf(hospitals), responseBody.path("totalCount").asInt());
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "병원 검색이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
    }

    private Hospital toHospital(JsonNode item) {
        String address = item.path("addr").asText(null);
        return new Hospital(
                item.path("yadmNm").asText(null),
                address == null || address.isBlank() ? null : address);
    }
}
