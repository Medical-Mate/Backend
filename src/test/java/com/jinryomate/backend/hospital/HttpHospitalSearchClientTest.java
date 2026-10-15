package com.jinryomate.backend.hospital;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.hospital.client.HttpHospitalSearchClient;
import com.jinryomate.backend.hospital.query.HospitalQuery;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 심평원에 실제로 나가는 주소.
 *
 * <p>이 클라이언트는 URI 를 손으로 조립한다. 키를 다시 인코딩하면 오류 없이 0건이 오기 때문이다.
 * 손으로 조립한 것은 손으로 틀리므로 나가는 문자열을 그대로 대조한다.
 */
class HttpHospitalSearchClientTest {

    private static final String BASE = "http://hira.test/svc/getHospBasisList";

    /** 공공데이터포털 키처럼 이미 인코딩된 꼴. 다시 인코딩되면 %253D 가 된다. */
    private static final String KEY = "abc%2Bdef%3D%3D";

    private static final String EMPTY = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
             "body":{"items":"","numOfRows":20,"pageNo":1,"totalCount":0}}}
            """;

    private MockRestServiceServer server;
    private HttpHospitalSearchClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpHospitalSearchClient(builder.build(), new ObjectMapper(),
                new HospitalProperties("http://hira.test/svc", KEY, Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("지역과 이름이 함께 풀리면 시도·시군구 코드와 이름을 다 싣는다")
    void 지역_이름() {
        server.expect(requestTo(BASE + "?serviceKey=" + KEY + "&_type=json&pageNo=1&numOfRows=20"
                        + "&sidoCd=110000&sgguCd=110022&yadmNm=%ED%94%BC%EB%B6%80%EA%B3%BC"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        client.search(new HospitalQuery("피부과", "110000", "110022"), 1, 20);

        server.verify();
    }

    @Test
    @DisplayName("지역만 풀리면 이름 조건을 싣지 않는다")
    void 지역만() {
        // yadmNm= 을 빈 값으로 보내지 않는다. 상류가 빈 값을 어떻게 읽는지에 기대지 않는다.
        server.expect(requestTo(BASE + "?serviceKey=" + KEY + "&_type=json&pageNo=2&numOfRows=10"
                        + "&sidoCd=110000&sgguCd=110022"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        client.search(new HospitalQuery(null, "110000", "110022"), 2, 10);

        server.verify();
    }

    @Test
    @DisplayName("시도까지만 풀리면 시군구 코드를 싣지 않는다")
    void 시도만() {
        server.expect(requestTo(BASE + "?serviceKey=" + KEY + "&_type=json&pageNo=1&numOfRows=20"
                        + "&sidoCd=110000&yadmNm=%ED%94%BC%EB%B6%80%EA%B3%BC"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        client.search(new HospitalQuery("피부과", "110000", null), 1, 20);

        server.verify();
    }

    @Test
    @DisplayName("지역을 못 풀면 지금처럼 검색어 전체를 이름으로 보낸다")
    void 이름만() {
        // 공백은 + 로 간다(URLEncoder). 이 PR 전과 같은 꼴이다.
        server.expect(requestTo(BASE + "?serviceKey=" + KEY + "&_type=json&pageNo=1&numOfRows=20"
                        + "&yadmNm=%EC%A4%91%EA%B5%AC+%EB%82%B4%EA%B3%BC"))
                .andRespond(withSuccess(EMPTY, MediaType.APPLICATION_JSON));

        client.search(HospitalQuery.byName("중구 내과"), 1, 20);

        server.verify();
    }
}
