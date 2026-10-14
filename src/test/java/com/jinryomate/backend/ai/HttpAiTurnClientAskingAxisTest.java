package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.ai.client.HttpAiTurnClient;
import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.ai.dto.AskingAxis;
import com.jinryomate.backend.intake.entity.IntakeSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * AI 응답의 {@code asking_axis} 를 어떻게 받는지(#162).
 *
 * <p>앱이 폰 추출 프롬프트에 <b>그대로</b> 넣는 값이라 가공하면 안 되고, 모양이 어긋나도
 * 턴 자체는 살아야 한다 — 이 값이 없으면 앱은 서버 추출로 보낼 뿐 문답은 이어진다.
 */
class HttpAiTurnClientAskingAxisTest {

    private static final String START = "/v1/previsit/sessions";

    @Test
    @DisplayName("asking_axis 의 key·label 을 가공하지 않고 받는다")
    void 그대로_받는다() {
        AiTurnResult result = start("""
                {"reply": "언제부터 그러셨어요?", "state": {},
                 "asking_axis": {"key": "onset", "label": "언제부터·어떻게 시작"}}""");

        assertThat(result.askingAxis()).isEqualTo(new AskingAxis("onset", "언제부터·어떻게 시작"));
    }

    @Test
    @DisplayName("asking_axis 가 null 이거나 없으면 null 이다")
    void 없으면_null() {
        assertThat(start("""
                {"reply": "편하게 말씀해 주세요.", "state": {}, "asking_axis": null}""").askingAxis()).isNull();

        // 이 필드가 생기기 전 이미지(sha-ce20921 이전)가 이 모양이다. 되돌려도 턴은 돈다.
        assertThat(start("""
                {"reply": "편하게 말씀해 주세요.", "state": {}}""").askingAxis()).isNull();
    }

    @Test
    @DisplayName("모양이 어긋나도 턴은 실패하지 않는다")
    void 모양이_어긋나도_턴은_산다() {
        // 레코드로 바로 받았다면 여기서 역직렬화 오류로 응답 전체가 떨어진다.
        AiTurnResult asString = start("""
                {"reply": "어떤 느낌이세요?", "state": {}, "asking_axis": "character"}""");
        assertThat(asString.reply()).isEqualTo("어떤 느낌이세요?");
        assertThat(asString.askingAxis()).isNull();

        AiTurnResult noKey = start("""
                {"reply": "어떤 느낌이세요?", "state": {}, "asking_axis": {"label": "어떤 느낌"}}""");
        assertThat(noKey.askingAxis()).isNull();
    }

    private AiTurnResult start(String response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai:8000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai:8000" + START))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        AiTurnResult result = new HttpAiTurnClient(builder.build(), new ObjectMapper(), new AiSigner("test-secret"))
                .start(IntakeSession.start(null, null, null, null, null));

        server.verify();
        return result;
    }
}
