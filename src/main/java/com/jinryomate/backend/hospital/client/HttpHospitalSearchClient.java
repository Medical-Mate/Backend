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
 * <pre>
 * 키를 그대로 실어 보냄   →  "서울" 4339건
 * 디코딩 후 재인코딩      →  0건 (오류 없음)
 * </pre>
 *
 * <p>그래서 <b>키만 인코딩하지 않고</b> 나머지 값만 {@link URLEncoder} 로 감싼다.
 */
@Slf4j
public class HttpHospitalSearchClient implements HospitalSearchClient {

    private static final String PATH = "/getHospBasisList";

    /** 심평원이 정상 응답에 싣는 코드. */
    private static final String OK = "00";

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

        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (Exception e) {
            // 응답 본문에 키가 되비쳐 올 수 있어 예외 메시지를 남기지 않는다.
            log.warn("병원 검색 호출 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "병원 검색이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
        }

        return parse(body);
    }

    /**
     * 응답을 푼다.
     *
     * <p>심평원은 결과가 없으면 {@code items} 를 <b>빈 문자열로</b> 준다. 객체도 배열도 아니라
     * 타입으로 받으면 깨진다. 한 건일 때는 {@code item} 이 배열이 아니라 <b>객체 하나</b>로 온다 —
     * 둘 다 {@link JsonNode} 로 다뤄야 안전하다.
     */
    private HospitalSearchResponse parse(String body) {
        if (body == null || body.isBlank()) {
            return new HospitalSearchResponse(List.of(), 0);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            // XML 오류 페이지가 오는 경우가 있다. 키가 틀렸거나 트래픽 초과일 때다.
            log.error("병원 검색 응답을 읽을 수 없습니다. JSON 이 아닙니다.");
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "병원 검색이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
        }

        JsonNode response = root.path("response");
        String code = response.path("header").path("resultCode").asText();
        if (!OK.equals(code)) {
            log.error("병원 검색 오류 resultCode={} resultMsg={}",
                    code, response.path("header").path("resultMsg").asText());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "병원 검색이 지연되고 있습니다. 잠시 후 다시 시도해주세요.");
        }

        JsonNode responseBody = response.path("body");
        JsonNode items = responseBody.path("items").path("item");

        List<Hospital> hospitals = new ArrayList<>();
        if (items.isArray()) {
            items.forEach(item -> hospitals.add(toHospital(item)));
        } else if (items.isObject()) {
            hospitals.add(toHospital(items));
        }

        return new HospitalSearchResponse(List.copyOf(hospitals), responseBody.path("totalCount").asInt());
    }

    private Hospital toHospital(JsonNode item) {
        String url = item.path("hospUrl").asText(null);
        return new Hospital(
                item.path("yadmNm").asText(null),
                url == null || url.isBlank() ? null : url);
    }
}
