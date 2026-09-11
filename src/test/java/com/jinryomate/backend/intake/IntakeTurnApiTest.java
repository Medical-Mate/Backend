package com.jinryomate.backend.intake;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.Side;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 문답 턴 처리.
 *
 * <p>세션을 시작만 하고 답변을 보낼 수 없던 구멍을 메운 API 다. 앱의 S2 화면
 * (와이어프레임 {@code 1c-1}~{@code 1c-4})이 이 경로로만 동작한다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IntakeTurnApiTest {

    private static final long KAKAO_ID = 7788990011L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired IntakeSessionRepository sessionRepository;

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
    @DisplayName("세션을 시작하면 AI 첫 질문이 이미 들어 있다")
    void 시작하면_첫_질문() throws Exception {
        // 앱이 세션을 만든 뒤 또 호출하지 않아도 바로 화면을 그릴 수 있어야 한다.
        mockMvc.perform(startSession())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(1))
                .andExpect(jsonPath("$.messages[0].role").value("AI"))
                .andExpect(jsonPath("$.messages[0].text").isNotEmpty())
                .andExpect(jsonPath("$.progress.current").value(0))
                .andExpect(jsonPath("$.progress.total").value(20));
    }

    @Test
    @DisplayName("부위 마스터에 없는 코드는 400이다")
    void 없는_부위() throws Exception {
        // AI 도 같은 검증을 하지만 그대로 넘기면 문답을 시작한 뒤에 422 가 돌아온다.
        // 앱은 이미 화면을 넘긴 뒤라 되돌리기가 어렵다.
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:999", null, "없는 곳"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("좌우가 없는 부위에 좌우를 보내면 400이다")
    void 좌우_없는_부위() throws Exception {
        // 34곳 중 13곳이 laterality: none 이다. 머리에는 좌우가 없다.
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("ANC:001", Side.LEFT, "머리"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("좌우")));
    }

    @Test
    @DisplayName("부위 없이 좌우만 보내면 400이다")
    void 부위_없이_좌우만() throws Exception {
        // 무엇의 좌우인지 알 수 없다.
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest(null, Side.RIGHT, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("부위를 건너뛰어도 시작할 수 있다")
    void 부위_없이_시작() throws Exception {
        // 와이어프레임에 "부위 짚기"를 건너뛰는 경로가 있다.
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest(null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteNodeId").doesNotExist());
    }

    @Test
    @DisplayName("답변을 보내면 대화가 두 줄 늘고 진행도가 오른다")
    void 턴_처리() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(send(sessionId, "한 3주쯤 됐어요. 요즘 더 아파요.", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.ended").value(false))
                // 첫 질문 1 + 내 답변 1 + 다음 질문 1
                .andExpect(jsonPath("$.messages.length()").value(3))
                .andExpect(jsonPath("$.messages[1].role").value("USER"))
                .andExpect(jsonPath("$.messages[2].role").value("AI"))
                .andExpect(jsonPath("$.progress.current").value(1))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("턴 수는 상한을 넘지 않는다")
    void 턴_상한() throws Exception {
        long sessionId = startAndGetId();

        // 스텁은 4번째에 끝내므로 그 뒤 호출은 카운트되지 않는다.
        for (int i = 0; i < 6; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i, null)).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.progress.total").value(20))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("문답이 끝나면 상태가 COMPLETED 가 되고 종료 사유가 남는다")
    void 종료() throws Exception {
        long sessionId = startAndGetId();

        // 스텁은 4번째 답변에서 끝낸다.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i, null))
                    .andExpect(jsonPath("$.ended").value(false));
        }

        mockMvc.perform(send(sessionId, "마지막 답변", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.endReason").value("complete"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("끝난 문답에 또 보내도 오류가 아니고 대화가 늘지 않는다")
    void 종료_후_전송() throws Exception {
        long sessionId = startAndGetId();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(send(sessionId, "답변 " + i, null)).andExpect(status().isOk());
        }

        String before = mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andReturn().getResponse().getContentAsString();
        int countBefore = objectMapper.readTree(before).path("messages").size();

        // 네트워크가 끊긴 사이에 끝났을 수 있다. 앱이 종료 시점을 정확히 몰라도 되게 한다.
        mockMvc.perform(send(sessionId, "더 할 말 있어요", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ended").value(true))
                .andExpect(jsonPath("$.messages.length()").value(countBefore));
    }

    @Test
    @DisplayName("음성으로 보내도 텍스트만 저장된다")
    void 음성_입력() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(send(sessionId, "밥 먹고 30분쯤 지나면 아파요", IntakeMessage.InputMethod.STT))
                .andExpect(status().isOk());

        // 녹음은 저장하지 않는다. 남는 것은 변환된 텍스트와 "음성이었다"는 사실뿐이다.
        var session = sessionRepository.findById(sessionId).orElseThrow();
        var userMessage = session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.USER)
                .findFirst().orElseThrow();
        assertThat(userMessage.getInputMethod()).isEqualTo(IntakeMessage.InputMethod.STT);
        assertThat(userMessage.getText()).isEqualTo("밥 먹고 30분쯤 지나면 아파요");
    }

    @Test
    @DisplayName("빈 발화는 400이다")
    void 빈_발화() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(send(sessionId, "   ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("2000자를 넘으면 400이다")
    void 너무_긴_발화() throws Exception {
        long sessionId = startAndGetId();

        // 컬럼 길이가 2000이다. 막지 않으면 저장 시점에 500이 난다.
        mockMvc.perform(send(sessionId, "가".repeat(2001), null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 세션에는 보낼 수 없고, 있는지조차 알려주지 않는다")
    void 남의_세션() throws Exception {
        long sessionId = startAndGetId();

        given(kakaoClient.resolveKakaoId(anyString())).willReturn(2233445566L);
        String otherToken = "Bearer " + login().accessToken();

        mockMvc.perform(post("/api/sessions/" + sessionId + "/messages")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SendMessageRequest("남의 문답에 끼어들기", null))))
                // 403 이 아니라 404 다. 403 은 "그 세션이 있다"는 것을 알려준다.
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AI 가 준 state 를 세션에 보관한다")
    void state_보관() throws Exception {
        long sessionId = startAndGetId();

        // 이 값이 없으면 다음 턴을 AI 에 이어 붙일 수 없다. AI 서버는 무상태다.
        var afterStart = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(afterStart.getAiState()).isNotBlank();

        mockMvc.perform(send(sessionId, "답변", null)).andExpect(status().isOk());

        var afterTurn = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(afterTurn.getAiState()).isNotBlank();
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.RequestBuilder startSession() throws Exception {
        return post("/api/sessions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new StartSessionRequest("SUR:032", null, "아랫배")));
    }

    private long startAndGetId() throws Exception {
        String body = mockMvc.perform(startSession())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
    }

    private org.springframework.test.web.servlet.RequestBuilder send(
            long sessionId, String text, IntakeMessage.InputMethod method) throws Exception {
        return post("/api/sessions/" + sessionId + "/messages")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SendMessageRequest(text, method)));
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
