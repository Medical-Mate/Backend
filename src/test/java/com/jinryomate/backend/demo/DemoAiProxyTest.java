package com.jinryomate.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.AiProperties;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.demo.service.DemoAiProxy;
import com.jinryomate.backend.demo.service.DemoEventRecorder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 웹 데모 통로가 <b>AI 의 답을 그대로 넘기는지</b>.
 *
 * <p>통합 테스트로는 여기가 안 잡힌다 — 테스트에 HMAC 시크릿이 없어 프록시 빈 자체가
 * 없고, 있어도 실제 AI 를 불러야 상태 코드가 갈린다. 실제로 <b>422 가 502 로 나가는
 * 버그가 배포까지 갔다가</b> AI 담당이 경로를 물어보면서 드러났다.
 */
class DemoAiProxyTest {

    private static final String PATH = "/v1/previsit/turns";

    @Test
    @DisplayName("AI 의 422 를 502 로 뭉개지 않는다")
    void 스키마_위반은_422_그대로() {
        // 뭉개면 브라우저가 "내 요청이 틀렸다"와 "AI 가 죽었다"를 구별할 수 없다.
        // retrieve() 는 4xx·5xx 에 기본 오류 처리가 걸려 있어 그냥 두면 예외가 된다.
        var response = forward(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"detail": "state 가 필요합니다"}"""));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(body(response)).contains("state 가 필요합니다");
    }

    @Test
    @DisplayName("예산 소진 503 도 그대로 넘긴다")
    void 예산_소진은_503_그대로() {
        // 503 을 502 로 바꾸면 "한도가 찼다"가 "서버가 죽었다"로 보이고, 웹이 재시도한다.
        var response = forward(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"detail": "일일 LLM 예산 소진"}"""));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(body(response)).contains("예산 소진");
    }

    @Test
    @DisplayName("정상 응답은 본문까지 그대로 온다")
    void 정상_응답() {
        var response = forward(withSuccess("""
                {"reply": "어떻게 불편하신가요?", "state": {"x": 1}, "request_id": "demo-1"}""",
                MediaType.APPLICATION_JSON));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(body(response)).contains("어떻게 불편하신가요?");
    }

    @Test
    @DisplayName("보낸 본문을 한 바이트도 바꾸지 않는다")
    void 본문을_안_바꾼다() {
        // 다시 직렬화하면 공백·키 순서가 달라져 서명이 틀어지고, 우리가 모르는 필드가
        // 조용히 떨어진다. 익명 데모는 patient_profile 을 브라우저가 직접 싣는다.
        String sent = "{\"request_id\":\"demo-1\",  \"patient_profile\":{\"medications\":[\"진통제\"]},"
                + "\"우리가_모르는_필드\":true}";

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000" + PATH))
                // 공백까지 그대로여야 한다. 서명이 이 바이트로 계산됐다.
                .andExpect(req -> assertThat(bodyOf(req)).isEqualTo(sent))
                .andExpect(header(AiSigner.REQUEST_ID_HEADER, "demo-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        proxy(builder).forward(PATH, sent.getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    @DisplayName("본문에 request_id 가 없으면 우리가 만들어 헤더에만 넣는다")
    void request_id_가_없을_때() {
        // 본문에 넣으면 바이트가 달라져 서명이 틀어진다. 양쪽 로그가 안 이어지는 것은
        // 감수하고, 문답을 막지는 않는다.
        String sent = "{\"state\":{}}";

        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000" + PATH))
                .andExpect(req -> assertThat(bodyOf(req)).isEqualTo(sent))
                .andExpect(header(AiSigner.REQUEST_ID_HEADER,
                        Matchers.not(Matchers.blankOrNullString())))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        proxy(builder).forward(PATH, sent.getBytes(StandardCharsets.UTF_8));
        server.verify();
    }

    // ---------- helpers ----------

    private ResponseEntity<byte[]> forward(
            org.springframework.test.web.client.ResponseCreator responder) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000" + PATH))
                .andExpect(header(AiSigner.SIGNATURE_HEADER, Matchers.matchesRegex("[0-9a-f]{64}")))
                .andRespond(responder);

        return proxy(builder).forward(PATH, "{\"state\":{}}".getBytes(StandardCharsets.UTF_8));
    }

    private DemoAiProxy proxy(RestClient.Builder builder) {
        // 기록은 여기서 볼 것이 아니다. 저장소가 없으면 record 가 조용히 삼키므로
        // 프록시의 행동은 그대로 확인된다 — 기록은 DemoServerEventTest 가 본다.
        return new DemoAiProxy(builder.build(), new ObjectMapper(),
                new AiProperties("http://ai:8000", "test-secret",
                        new AiProperties.Timeout(Duration.ofSeconds(30), Duration.ofSeconds(30))),
                new DemoEventRecorder(null));
    }

    private String body(ResponseEntity<byte[]> response) {
        return new String(response.getBody(), StandardCharsets.UTF_8);
    }

    private String bodyOf(org.springframework.http.client.ClientHttpRequest request) {
        return new String(((org.springframework.mock.http.client.MockClientHttpRequest) request)
                .getBodyAsBytes(), StandardCharsets.UTF_8);
    }
}
