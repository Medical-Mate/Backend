package com.jinryomate.backend.ai;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiTurnClient;
import com.jinryomate.backend.ai.dto.PhoneQuestionCandidates;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * 통증 강도를 AI 에 <b>어디에, 어떤 모양으로</b> 넘기는지(#168).
 *
 * <p>강도는 문답 전에 슬라이더로 받고 문답은 강도를 묻지 않는다. 그래서 질문 후보를 받을 때
 * {@code selections} 로 실어야 AI 가 강도를 반영한 카드로 후보를 만든다. 빠뜨려도 200 이 나가고
 * 후보도 멀쩡해 보여서 밖에서는 안 보인다 — 여기서 잠근다.
 *
 * <p><b>기대는 요청을 보내기 전에 전부 건다</b>({@link HttpAiTurnClientTest} 와 같은 이유).
 */
class HttpAiTurnClientSeverityTest {

    private static final String TURNS = "/v1/previsit/turns";
    private static final String SESSIONS = "/v1/previsit/sessions";

    private static final String RESPONSE = """
            {"reply": "감사해요.", "ended": true, "state": {}, "card": null}""";

    @Test
    @DisplayName("서버 후보 요청에 강도를 selections 로 싣는다 — 값은 카드와 같은 \"3 (꽤 아파요)\"")
    void 서버_후보에_강도() {
        call(TURNS, client -> client.requestQuestionCandidates(session(3, "꽤 아파요"), null),
                jsonPath("$.question_candidates").value(true),
                jsonPath("$.selections.length()").value(1),
                jsonPath("$.selections[0].axis").value("severity"),
                jsonPath("$.selections[0].value").value("3 (꽤 아파요)"));
    }

    @Test
    @DisplayName("폰 후보 요청에도 같은 강도를 싣는다 — 검증·보충이 강도가 반영된 카드로 돈다")
    void 폰_후보에_강도() {
        call(TURNS, client -> client.submitPhoneQuestionCandidates(session(2, "조금 아파요"), null,
                        new PhoneQuestionCandidates(List.of(), null)),
                jsonPath("$.phone_question_candidates").isArray(),
                jsonPath("$.selections[0].axis").value("severity"),
                jsonPath("$.selections[0].value").value("2 (조금 아파요)"));
    }

    @Test
    @DisplayName("라벨이 없으면 숫자만 싣는다 — 우리가 문구를 지어 넣지 않는다")
    void 라벨_없음() {
        call(TURNS, client -> client.requestQuestionCandidates(session(4, null), null),
                jsonPath("$.selections[0].value").value("4"));
    }

    @Test
    @DisplayName("강도를 안 골랐으면 selections 키가 없다")
    void 강도_없음() {
        call(TURNS, client -> client.requestQuestionCandidates(
                        IntakeSession.start(null, null, null, null, null), null),
                jsonPath("$.question_candidates").value(true),
                jsonPath("$.selections").doesNotExist());
    }

    @Test
    @DisplayName("세션 시작 요청에는 강도를 싣지 않는다 — AI 가 모르는 필드를 422 로 거부한다")
    void 시작에는_안_실음() {
        call(SESSIONS, client -> client.start(session(3, "꽤 아파요")),
                jsonPath("$.selections").doesNotExist(),
                jsonPath("$.severity").doesNotExist());
    }

    private static IntakeSession session(int level, String label) {
        IntakeSession session = IntakeSession.start(null, null, null, null, null);
        session.recordSeverity(level, label);
        return session;
    }

    private void call(String path, Consumer<HttpAiTurnClient> request, RequestMatcher... matchers) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        var expectation = server.expect(requestTo("http://ai:8000" + path));
        for (RequestMatcher matcher : matchers) {
            expectation.andExpect(matcher);
        }
        expectation.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        request.accept(new HttpAiTurnClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret")));

        server.verify();
    }
}
