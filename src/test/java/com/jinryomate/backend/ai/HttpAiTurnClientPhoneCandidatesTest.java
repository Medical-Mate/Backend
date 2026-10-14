package com.jinryomate.backend.ai;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiTurnClient;
import com.jinryomate.backend.ai.dto.PatientProfile;
import com.jinryomate.backend.ai.dto.PhoneQuestionCandidates;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

/**
 * 폰 질문 후보를 AI 에 <b>무엇으로</b> 넘기는지(#164).
 *
 * <p><b>기대는 요청을 보내기 전에 전부 건다</b>({@link HttpAiTurnClientTest} 와 같은 이유).
 */
class HttpAiTurnClientPhoneCandidatesTest {

    private static final String TURNS = "/v1/previsit/turns";

    private static final String RESPONSE = """
            {"reply": "감사해요.", "ended": true, "state": {}, "card": null}""";

    @Test
    @DisplayName("폰 후보·메타·건강정보를 question_candidates: true 와 함께 넘긴다")
    void 다_실어_보낸다() {
        var meta = new ObjectMapper().createObjectNode().put("model_id", "qwen3.5-2b");
        submit(new PhoneQuestionCandidates(
                        List.of(new PhoneQuestionCandidates.Item("진통제를 계속 먹어도 되나요?", "medications")),
                        meta),
                new PatientProfile(List.of("진통제"), null, null),
                jsonPath("$.question_candidates").value(true),
                jsonPath("$.phone_question_candidates[0].text").value("진통제를 계속 먹어도 되나요?"),
                jsonPath("$.phone_question_candidates[0].source").value("medications"),
                jsonPath("$.candidates_meta.model_id").value("qwen3.5-2b"),
                jsonPath("$.patient_profile.medications[0]").value("진통제"),
                // 끝난 문답이라 다음 질문이 필요 없다. 발화를 보내지 않는다.
                jsonPath("$.utterance").doesNotExist());
    }

    @Test
    @DisplayName("빈 목록도 키째 보낸다 — 빠지면 AI 가 서버 LLM 으로 후보를 만든다")
    void 빈_목록도_키째() {
        submit(new PhoneQuestionCandidates(List.of(), null), null,
                jsonPath("$.phone_question_candidates").isArray(),
                jsonPath("$.phone_question_candidates.length()").value(0),
                // 메타가 없으면 키도 없다. AI 는 메타만 오면 422 다.
                jsonPath("$.candidates_meta").doesNotExist());
    }

    private void submit(PhoneQuestionCandidates phone, PatientProfile profile, RequestMatcher... matchers) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        var expectation = server.expect(requestTo("http://ai:8000" + TURNS));
        for (RequestMatcher matcher : matchers) {
            expectation.andExpect(matcher);
        }
        expectation.andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        new HttpAiTurnClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .submitPhoneQuestionCandidates(IntakeSession.start(null, null, null, null, null), profile, phone);

        server.verify();
    }
}
