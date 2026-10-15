package com.jinryomate.backend.intake;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionsRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SeverityRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 놓은 문답(#174) — 새 문답을 시작하면 카드까지 못 간 이전 문답은 {@code ABANDONED} 가 된다.
 *
 * <p>출발점은 PR #173 리뷰에서 나온 경로다. 카드를 지우면 세션도 함께 지워지는데, 그때 옛 문답이
 * "가장 최근"이 되어 홈에 되살아났다.
 *
 * <p>스텁 AI 는 네 번째 답변에서 끝낸다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AbandonSessionApiTest {

    private static final long KAKAO_ID = 6655443322L;

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

    // ---------- 리뷰에서 나온 경로 ----------

    @Test
    @DisplayName("카드를 지워도 옛 문답이 이어서 하기로 되살아나지 않는다 (진행 중이던 문답)")
    void 삭제_뒤_진행중_문답() throws Exception {
        start();                                // A: 하다가 나갔다
        long cardB = createCard(start());       // B: 카드까지 만들었다

        mockMvc.perform(delete("/api/cards/" + cardB).header("Authorization", token))
                .andExpect(status().isNoContent());

        // B 세션이 함께 지워져 A 가 "가장 최근"이 된다. A 가 놓여 있지 않으면 여기서 뜬다.
        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(jsonPath("$.inProgressSession").doesNotExist());
    }

    @Test
    @DisplayName("카드를 지워도 옛 문답이 되살아나지 않는다 (끝났지만 질문 고르기에서 멈춘 문답)")
    void 삭제_뒤_끝난_문답() throws Exception {
        answerUntilEnd(start());                // A: 문답은 끝, 카드는 없음
        long cardB = createCard(start());

        mockMvc.perform(delete("/api/cards/" + cardB).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(jsonPath("$.inProgressSession").doesNotExist());
    }

    // ---------- 전이 ----------

    @Test
    @DisplayName("새 문답을 시작하면 카드 없는 이전 문답이 ABANDONED 가 된다")
    void 새_문답이면_이전_것을_놓는다() throws Exception {
        long running = start();
        long ended = start();
        answerUntilEnd(ended);
        long latest = start();

        session(running).andExpect(jsonPath("$.status").value("ABANDONED"));
        session(ended).andExpect(jsonPath("$.status").value("ABANDONED"));
        session(latest).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("카드까지 간 문답은 건드리지 않는다")
    void 카드_있는_문답은_그대로() throws Exception {
        long withCard = start();
        createCard(withCard);
        start();

        session(withCard).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("AI 시작이 실패하면 이전 문답을 놓지 않는다")
    void 시작_실패면_되돌림() throws Exception {
        long previous = start();

        doThrow(new ApiException(ErrorCode.SERVICE_UNAVAILABLE)).when(aiTurnClient).start(any());
        mockMvc.perform(startRequest()).andExpect(status().isServiceUnavailable());

        // 새 문답은 안 열렸는데 이전 것만 놓이면, 환자는 이어서 할 문답을 통째로 잃는다.
        session(previous).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    // ---------- 놓은 문답에 쓰기 ----------

    @Test
    @DisplayName("놓은 문답에 쓰면 409 SESSION_ABANDONED 이고, 조회는 된다")
    void 놓은_문답에_쓰기() throws Exception {
        long abandoned = start();
        start();

        // 받아 주면 홈에도 안 보이는 세션에 대화가 쌓인다.
        expectAbandoned(post("/api/sessions/" + abandoned + "/messages")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendMessageRequest("답변", null, null, null))));
        expectAbandoned(put("/api/sessions/" + abandoned + "/severity")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SeverityRequest(3, null))));
        expectAbandoned(put("/api/sessions/" + abandoned + "/questions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuestionsRequest(List.of("질문")))));
        expectAbandoned(post("/api/sessions/" + abandoned + "/question-candidates")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidates\": []}"));
        expectAbandoned(post("/api/sessions/" + abandoned + "/card")
                .header("Authorization", token));

        // 조회는 막지 않는다 — 앱이 왜 막혔는지 볼 수 있어야 한다.
        session(abandoned).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private void expectAbandoned(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_ABANDONED"));
    }

    private org.springframework.test.web.servlet.ResultActions session(long sessionId) throws Exception {
        return mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token));
    }

    private RequestBuilder startRequest() throws Exception {
        return post("/api/sessions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new StartSessionRequest("SUR:032", null, null, "아랫배", null)));
    }

    private long start() throws Exception {
        String body = mockMvc.perform(startRequest())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
    }

    private void answerUntilEnd(long sessionId) throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/sessions/" + sessionId + "/messages")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SendMessageRequest("답변 " + i, null, null, null))))
                    .andExpect(jsonPath("$.ended").value(i == 3));
        }
    }

    private long createCard(long sessionId) throws Exception {
        String card = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(card).path("cardId").asLong();
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
