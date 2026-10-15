package com.jinryomate.backend.intake;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.ai.client.AiTurnClient;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.AiProfile;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 폰이 만든 질문 후보(#164).
 *
 * <p>앱은 폰 경로만 쓴다(#157). 온디바이스 세션에서 서버가 후보를 미리 만들면 카드가 외부
 * LLM 으로 나가므로, <b>그 호출이 안 나가는 것</b>을 먼저 잠근다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PhoneQuestionCandidatesApiTest {

    private static final long KAKAO_ID = 5544332211L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;
    @MockitoSpyBean AiTurnClient aiTurnClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
        completeOnboarding();
    }

    @Test
    @DisplayName("온디바이스 세션은 끝나도 서버가 질문 후보를 미리 만들지 않는다")
    void 서버_후보를_안_만든다() throws Exception {
        long sessionId = completedSession(AiProfile.ONDEVICE);

        // 이 호출이 나가면 카드가 Bedrock 으로 간다 — 폰 경로만 쓰기로 한 전제가 깨진다.
        verify(aiTurnClient, never()).requestQuestionCandidates(any(), any());
        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.questionCandidates.length()").value(0));
    }

    @Test
    @DisplayName("서버 세션은 지금처럼 끝날 때 서버 후보를 받는다")
    void 서버_세션은_그대로() throws Exception {
        completedSession(AiProfile.SERVER);
        verify(aiTurnClient).requestQuestionCandidates(any(), any());
    }

    @Test
    @DisplayName("폰 후보를 보내면 AI 가 돌려준 후보로 갈아끼운다")
    void 폰_후보로_갈아끼운다() throws Exception {
        long sessionId = completedSession(AiProfile.ONDEVICE);

        mockMvc.perform(submit(sessionId, """
                        {"candidates": [
                           {"text": "계단 내려갈 때 더 아픈 건 괜찮은 건가요?", "source": "exacerbating_relieving"},
                           {"text": "진통제를 계속 먹어도 되나요?"}
                         ],
                         "candidatesMeta": {"model_id": "qwen3.5-2b", "prompt_version": "q-small-v4"}}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionCandidates.length()").value(2))
                .andExpect(jsonPath("$.questionCandidates[0].text").value("계단 내려갈 때 더 아픈 건 괜찮은 건가요?"))
                .andExpect(jsonPath("$.questionCandidates[0].origin").value("model"));

        // 저장까지 됐는지 — 재조회에도 같은 후보여야 질문 고르기(1c-D)를 다시 그릴 수 있다.
        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.questionCandidates.length()").value(2));
    }

    @Test
    @DisplayName("폰이 하나도 못 만들었으면 빈 배열을 보내고, 템플릿으로 채워진다")
    void 빈_배열() throws Exception {
        long sessionId = completedSession(AiProfile.ONDEVICE);

        mockMvc.perform(submit(sessionId, """
                        {"candidates": []}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionCandidates[0].origin").value("template"));
    }

    @Test
    @DisplayName("문답이 끝나기 전에는 400 이다")
    void 끝나기_전() throws Exception {
        long sessionId = start(AiProfile.ONDEVICE);

        mockMvc.perform(submit(sessionId, """
                        {"candidates": []}"""))
                .andExpect(status().isBadRequest());
        verify(aiTurnClient, never()).submitPhoneQuestionCandidates(any(), any(), any());
    }

    @Test
    @DisplayName("후보 목록을 빼먹으면 400 이다")
    void 목록_없음() throws Exception {
        long sessionId = completedSession(AiProfile.ONDEVICE);

        // null 을 빈 배열로 받아 주면 "폰이 못 만들었다" 와 "앱이 필드를 안 실었다" 가 섞인다.
        mockMvc.perform(submit(sessionId, "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("세션 응답에 카드 축 값·상태가 실린다")
    void 카드_축() throws Exception {
        long sessionId = start(AiProfile.ONDEVICE);
        mockMvc.perform(send(sessionId, "아랫배가 콕콕 쑤셔요")).andExpect(status().isOk());

        // 폰이 후보를 만드는 재료다. AI 가 준 축을 그대로 낸다.
        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.cardAxes.length()").value(8))
                .andExpect(jsonPath("$.cardAxes[0].key").value("site"))
                .andExpect(jsonPath("$.cardAxes[0].status").value("filled"))
                .andExpect(jsonPath("$.cardAxes[0].value").value("아랫배가 콕콕 쑤셔요"))
                .andExpect(jsonPath("$.cardAxes[1].status").value("not_asked"))
                .andExpect(jsonPath("$.cardAxes[1].value").doesNotExist());
    }

    // ---------- helpers ----------

    private long completedSession(AiProfile profile) throws Exception {
        long sessionId = start(profile);
        // 스텁은 4번째 답변에서 끝낸다.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i)).andExpect(status().isOk());
        }
        return sessionId;
    }

    private long start(AiProfile profile) throws Exception {
        String body = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:032", profile, null, "아랫배", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
    }

    private org.springframework.test.web.servlet.RequestBuilder send(long sessionId, String text)
            throws Exception {
        return post("/api/sessions/" + sessionId + "/messages")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendMessageRequest(text, null, null, null)));
    }

    private org.springframework.test.web.servlet.RequestBuilder submit(long sessionId, String json) {
        return post("/api/sessions/" + sessionId + "/question-candidates")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private void completeOnboarding() throws Exception {
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "김서연", 1994, "03-03", Sex.FEMALE,
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new TextFieldRequest(FieldStatus.UNKNOWN, null)))))
                .andExpect(status().isOk());
    }

    private TokenResponse login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }
}
