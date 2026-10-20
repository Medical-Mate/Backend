package com.jinryomate.backend.intake;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
 * 질문 고르기를 마쳤는지(#185). 앱이 카드 없는 문답을 "작성 중"과 "임시 저장"으로 가른다.
 *
 * <p>스텁 AI 는 네 번째 답변에서 끝낸다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuestionsConfirmedApiTest {

    private static final long KAKAO_ID = 4433221100L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
        completeOnboarding();
    }

    @Test
    @DisplayName("채팅이 끝나도 다음을 누르기 전에는 작성 중이고, 빈 목록으로 다음을 눌러도 임시 저장이 된다")
    void 다음을_눌러야_임시_저장() throws Exception {
        long sessionId = start();
        session(sessionId).andExpect(jsonPath("$.questionsConfirmed").value(false));

        // 채팅은 끝났지만 질문 고르기에서 나갔다. ended 만 보면 여기서 "임시 저장"이 된다.
        answerUntilEnd(sessionId);
        home().andExpect(jsonPath("$.inProgressSession.ended").value(true))
                .andExpect(jsonPath("$.inProgressSession.questionsConfirmed").value(false));

        // 질문을 하나도 안 고르고 다음. 빈 목록이 "아직 안 누름"과 갈려야 한다.
        putQuestions(sessionId, List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsConfirmed").value(true));

        session(sessionId).andExpect(jsonPath("$.questionsConfirmed").value(true));
        home().andExpect(jsonPath("$.inProgressSession.questionsConfirmed").value(true));
    }

    @Test
    @DisplayName("끝나기 전에 받은 목록은 저장만 하고 마친 것으로 보지 않는다")
    void 끝나기_전_목록은_마친_것이_아니다() throws Exception {
        // 질문 고르기는 채팅이 끝난 뒤에 나온다. 그 전에 받은 것을 마친 것으로 남기면
        // 채팅 중인 문답이 "임시 저장"으로 보이고 이어서 하기가 채팅을 건너뛴다.
        long sessionId = start();

        putQuestions(sessionId, List.of("질문"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0]").value("질문"))
                .andExpect(jsonPath("$.questionsConfirmed").value(false));

        answerUntilEnd(sessionId);
        session(sessionId).andExpect(jsonPath("$.questionsConfirmed").value(false));
    }

    // ---------- helpers ----------

    private ResultActions putQuestions(long sessionId, List<String> questions) throws Exception {
        return mockMvc.perform(put("/api/sessions/" + sessionId + "/questions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuestionsRequest(questions))));
    }

    private ResultActions session(long sessionId) throws Exception {
        return mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token));
    }

    private ResultActions home() throws Exception {
        return mockMvc.perform(get("/api/me/home").header("Authorization", token));
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
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }
}
