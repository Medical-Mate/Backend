package com.jinryomate.backend.intake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.jinryomate.backend.intake.dto.IntakeDtos.SeverityRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.AiProfile;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문답 3단계 흐름(#168) — 부위 짚기 → 통증 강도 → 문답·질문 고르기.
 *
 * <p>강도가 문답 <b>전</b>으로 왔고, 질문 고르기가 별도 화면이 아니라 채팅 안으로 들어왔다.
 * 그래서 강도는 시작 요청에 함께 오고, 후보는 마지막 턴 응답에 바로 실려야 한다.
 *
 * <p>스텁 AI 는 네 번째 답변에서 끝낸다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ThreeStepFlowApiTest {

    private static final long KAKAO_ID = 7766554433L;

    /** {@code StubAiTurnClient.STUB_SERVER_CANDIDATE} 와 같은 값. 스텁이 서버 후보로 내는 문구다. */
    private static final String STUB_CANDIDATE = "검사를 받아야 하나요?";

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

    // ---------- 시작 요청의 강도 ----------

    @Test
    @DisplayName("시작 요청에 강도를 실으면 세션에 남는다")
    void 시작에_강도() throws Exception {
        mockMvc.perform(start(AiProfile.SERVER, new SeverityRequest(3, "꽤 아파요")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity.level").value(3))
                .andExpect(jsonPath("$.severity.label").value("꽤 아파요"));
    }

    @Test
    @DisplayName("시작 요청의 강도도 1~5 밖이면 400 이고, 세션이 열리지 않는다")
    void 시작_강도_범위() throws Exception {
        mockMvc.perform(start(AiProfile.SERVER, new SeverityRequest(6, null)))
                .andExpect(status().isBadRequest());

        // 중첩 객체 검증이 꺼져 있으면 level 없는 강도가 통과해 NPE 로 500 이 난다.
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"siteNodeId": "SUR:032", "siteText": "아랫배",
                                 "severity": {"label": "꽤 아파요"}}"""))
                .andExpect(status().isBadRequest());

        // 400 이 났으면 AI 시작 요청도 나가지 않았어야 한다.
        verify(aiTurnClient, never()).start(any());
    }

    @Test
    @DisplayName("질문 후보를 받을 때 세션에 강도가 들어 있다 — AI 에 selections 로 넘어가는 값이다")
    void 후보_요청에_강도() throws Exception {
        long sessionId = sessionId(start(AiProfile.SERVER, new SeverityRequest(3, "꽤 아파요")));
        answerUntilEnd(sessionId);

        // 실제 본문 모양은 HttpAiTurnClientSeverityTest 가 본다. 여기서는 그 시점에
        // 세션이 강도를 들고 있는지만 본다 — 시작 요청의 값이 여기까지 와야 한다.
        ArgumentCaptor<IntakeSession> captor = ArgumentCaptor.forClass(IntakeSession.class);
        verify(aiTurnClient).requestQuestionCandidates(captor.capture(), any());
        assertThat(captor.getValue().severityValue()).isEqualTo("3 (꽤 아파요)");
    }

    // ---------- 턴 응답의 후보 ----------

    @Test
    @DisplayName("서버 세션은 마지막 턴 응답에 후보가 바로 실리고, 그 전 턴은 빈 배열이다")
    void 마지막_턴에_후보() throws Exception {
        long sessionId = sessionId(start(AiProfile.SERVER, new SeverityRequest(3, "꽤 아파요")));

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i))
                    .andExpect(jsonPath("$.ended").value(false))
                    // null 이 아니라 빈 배열이어야 앱이 분기 없이 그린다.
                    .andExpect(jsonPath("$.questionCandidates").isArray())
                    .andExpect(jsonPath("$.questionCandidates.length()").value(0));
        }

        mockMvc.perform(send(sessionId, "마지막 답변"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.questionCandidates.length()").value(1))
                .andExpect(jsonPath("$.questionCandidates[0].text").value(STUB_CANDIDATE));
    }

    @Test
    @DisplayName("끝난 뒤 다시 보내도 후보가 실린다 — 마지막 응답을 잃은 앱이 여기서 받는다")
    void 끝난_뒤_재전송() throws Exception {
        long sessionId = sessionId(start(AiProfile.SERVER, null));
        answerUntilEnd(sessionId);

        mockMvc.perform(send(sessionId, "또 보냄"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.questionCandidates[0].text").value(STUB_CANDIDATE));
    }

    @Test
    @DisplayName("온디바이스 세션은 마지막 턴에도 빈 배열이다 — 후보는 폰이 만든다")
    void 온디바이스_마지막_턴() throws Exception {
        long sessionId = sessionId(start(AiProfile.ONDEVICE, new SeverityRequest(2, "조금 아파요")));
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i)).andExpect(status().isOk());
        }

        mockMvc.perform(send(sessionId, "마지막 답변"))
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.questionCandidates.length()").value(0));
    }

    // ---------- helpers ----------

    private void answerUntilEnd(long sessionId) throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i)).andExpect(status().isOk());
        }
    }

    private RequestBuilder start(AiProfile profile, SeverityRequest severity) throws Exception {
        return post("/api/sessions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new StartSessionRequest("SUR:032", profile, null, "아랫배", severity)));
    }

    private long sessionId(RequestBuilder start) throws Exception {
        String body = mockMvc.perform(start)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
    }

    private RequestBuilder send(long sessionId, String text) throws Exception {
        return post("/api/sessions/" + sessionId + "/messages")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendMessageRequest(text, null, null, null)));
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
