package com.jinryomate.backend.intake;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionsRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드까지 가지 않은 문답 지우기(#180). 카드 목록 편집(1j-4-D2)에서 문답 줄을 고른 경로다.
 *
 * <p>스텁 AI 는 네 번째 답변에서 끝낸다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeleteSessionApiTest {

    private static final long KAKAO_ID = 5544332211L;
    private static final long OTHER_KAKAO_ID = 5544332212L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EntityManager em;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.resolveKakaoId("other-token")).willReturn(OTHER_KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login("kakao-token").accessToken();
        completeOnboarding();
    }

    @Test
    @DisplayName("진행 중인 문답을 지우면 사라지고 홈에 다시 나오지 않는다")
    void 진행중_문답_삭제() throws Exception {
        start();                    // 놓인 옛 문답(#174)
        long latest = start();

        deleteSession(latest);

        session(latest).andExpect(status().isNotFound());
        // 옛 문답은 놓여 있어서 "가장 최근"이 되어도 되살아나지 않는다.
        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(jsonPath("$.inProgressSession").doesNotExist());
    }

    @Test
    @DisplayName("끝났지만 카드가 없는 문답도 대화·질문째 지운다")
    void 끝난_문답_삭제() throws Exception {
        // 질문 고르기(1c-D)에서 멈춘 문답. 대화와 고른 질문이 딸려 있어 함께 지워져야
        // 외래키에 안 걸리고, 증상이 서버에 남지 않는다.
        long sessionId = start();
        answerUntilEnd(sessionId);
        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionsRequest(List.of("질문")))))
                .andExpect(status().isOk());

        deleteSession(sessionId);

        session(sessionId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("놓은 문답도 지울 수 있다")
    void 놓은_문답_삭제() throws Exception {
        long abandoned = start();
        start();

        deleteSession(abandoned);

        session(abandoned).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카드를 만든 문답은 409 SESSION_HAS_CARD 이고 최신 카드 id 를 준다")
    void 카드_있는_문답은_거절() throws Exception {
        long sessionId = start();
        long cardId = createCard(sessionId);

        mockMvc.perform(delete("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SESSION_HAS_CARD"))
                .andExpect(jsonPath("$.error.details.cardId").value((int) cardId));

        // 문답도 카드도 그대로다.
        session(sessionId).andExpect(status().isOk());
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("남의 문답은 404 이고 지워지지 않는다")
    void 남의_문답() throws Exception {
        long mine = start();
        String other = "Bearer " + login("other-token").accessToken();

        mockMvc.perform(delete("/api/sessions/" + mine).header("Authorization", other))
                .andExpect(status().isNotFound());

        session(mine).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    /**
     * 지우고 <b>DB 까지 내려보낸다.</b> 테스트가 한 트랜잭션이라 그냥 두면 DELETE 가 롤백 전까지
     * 안 나가서, 딸린 행이 외래키에 걸려도 통과한다.
     */
    private void deleteSession(long sessionId) throws Exception {
        mockMvc.perform(delete("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(status().isNoContent());
        em.flush();
        em.clear();
    }

    private ResultActions session(long sessionId) throws Exception {
        return mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token));
    }

    private long start() throws Exception {
        String body = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:032", null, null, "아랫배", null))))
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

    private TokenResponse login(String kakaoToken) throws Exception {
        String body = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest(kakaoToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }
}
