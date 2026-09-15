package com.jinryomate.backend.ai;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiTurnClient;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.entity.Side;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * 세션을 시작할 때 <b>부위를 무엇으로 넘기는지</b>.
 *
 * <p>짚은 부위의 이름({@code siteText})을 들고만 있고 AI 에는 안 넘기고 있었다. 그래서
 * 앱이 노드 id 를 안 보내던 동안 AI 는 부위를 아예 몰랐고, "어디가 불편하신지" 부터 다시
 * 물었다. 짚은 곳과 다른 부위를 말해도 되묻지 못했다.
 *
 * <p><b>기대는 요청을 보내기 전에 전부 걸어야 한다.</b> 처음에 호출한 뒤에 붙였더니
 * 이미 지나간 요청이라 평가되지 않았고, 일부러 틀린 값을 넣어도 통과했다.
 */
class HttpAiTurnClientTest {

    private static final String START = "/v1/previsit/sessions";

    private static final String OPENING = """
            {"reply": "어떻게 불편하신가요?", "state": {}, "card": null, "request_id": null}""";

    @Test
    @DisplayName("노드 id 가 없어도 짚은 부위 이름을 넘긴다")
    void 이름만_있을_때() {
        // 앱이 지금 이 상태다 — siteText 는 보내고 siteNodeId 는 안 보낸다.
        start(session(null, "팔", null),
                jsonPath("$.site_label").value("팔"),
                jsonPath("$.site_node_id").doesNotExist());
    }

    @Test
    @DisplayName("노드 id 가 있으면 둘 다 넘긴다")
    void 둘_다_있을_때() {
        // 계약이 "site_node_id 가 있으면 site_label 은 무시한다" 라 둘 다 실어도 안전하다.
        start(session("SUR:051", "팔", Side.LEFT),
                jsonPath("$.site_node_id").value("SUR:051"),
                jsonPath("$.site_label").value("팔"),
                jsonPath("$.side").value("left"));
    }

    @Test
    @DisplayName("부위를 건너뛰었으면 아무것도 안 넘긴다")
    void 부위를_건너뛰었을_때() {
        // 부위 짚기를 건너뛰는 경로가 있다. 빈 값을 지어내 보내면 AI 가 없는 부위를
        // 짚었다고 읽는다.
        start(session(null, null, null),
                jsonPath("$.site_label").doesNotExist(),
                jsonPath("$.site_node_id").doesNotExist());
    }

    @Test
    @DisplayName("이름이 공백뿐이면 안 넘긴다")
    void 공백뿐일_때() {
        start(session(null, "   ", null),
                jsonPath("$.site_label").doesNotExist());
    }

    /**
     * 세션 시작을 한 번 부른다. <b>기대를 먼저 걸고</b> 호출한다 — 순서가 반대면
     * 이미 지나간 요청이라 검사되지 않는다.
     */
    private void start(IntakeSession session, RequestMatcher... matchers) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        var expectation = server.expect(requestTo("http://ai:8000" + START));
        for (RequestMatcher matcher : matchers) {
            expectation.andExpect(matcher);
        }
        expectation.andRespond(withSuccess(OPENING, MediaType.APPLICATION_JSON));

        new HttpAiTurnClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .start(session);

        // 아예 안 불렀는데 통과하는 것도 막는다.
        server.verify();
    }

    private IntakeSession session(String nodeId, String siteText, Side side) {
        return IntakeSession.start(null, nodeId, side, siteText, null);
    }
}
