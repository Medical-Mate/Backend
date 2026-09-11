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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증상 정리 3·4단계 — 통증 강도와 의사에게 물어볼 것.
 *
 * <p>둘 다 <b>문답이 끝난 뒤</b> 화면이다. {@code COMPLETED} 라고 막으면 정상 흐름이
 * 막히므로 그게 되는지가 이 테스트의 핵심이다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IntakeStepApiTest {

    private static final long KAKAO_ID = 9911223344L;

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

    // ---------- 통증 강도 ----------

    @Test
    @DisplayName("통증 강도를 기록하면 세션 조회에 그대로 나온다")
    void 강도_기록() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(severity(sessionId, 3, "꽤 아파요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity.level").value(3))
                .andExpect(jsonPath("$.severity.label").value("꽤 아파요"));

        // 뒤로 갔다 오면 고른 값이 복원돼야 한다.
        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.severity.level").value(3));
    }

    @Test
    @DisplayName("아직 안 골랐으면 severity 가 null 이다")
    void 강도_미선택() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(jsonPath("$.severity").doesNotExist());
    }

    @Test
    @DisplayName("다시 고르면 덮어쓴다")
    void 강도_변경() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(severity(sessionId, 2, "조금 아파요")).andExpect(status().isOk());
        mockMvc.perform(severity(sessionId, 5, "매우 심함"))
                .andExpect(jsonPath("$.severity.level").value(5))
                .andExpect(jsonPath("$.severity.label").value("매우 심함"));
    }

    @Test
    @DisplayName("1~5 밖의 값은 400이다")
    void 강도_범위() throws Exception {
        long sessionId = startAndGetId();

        // 1~5 서열척도다. NRS 0~10 으로 착각해 0 이나 10 을 보내면 여기서 걸린다.
        mockMvc.perform(severity(sessionId, 0, null)).andExpect(status().isBadRequest());
        mockMvc.perform(severity(sessionId, 6, null)).andExpect(status().isBadRequest());
        mockMvc.perform(severity(sessionId, 10, null)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("라벨 없이 숫자만 보내도 된다")
    void 강도_라벨_없음() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(severity(sessionId, 4, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.severity.level").value(4));
    }

    // ---------- 의사에게 물어볼 것 ----------

    @Test
    @DisplayName("질문 목록을 저장하면 순서가 보존된다")
    void 질문_저장() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(questions(sessionId, List.of(
                        "혈액 검사를 받아야 하나요?",
                        "지금 먹는 약과 같이 먹어도 되나요?",
                        "어떤 증상이면 다시 와야 하나요?")))
                .andExpect(status().isOk())
                // 화면에 ①②③ 번호가 붙으므로 순서가 뒤바뀌면 안 된다.
                .andExpect(jsonPath("$.questions[0]").value("혈액 검사를 받아야 하나요?"))
                .andExpect(jsonPath("$.questions[2]").value("어떤 증상이면 다시 와야 하나요?"));
    }

    @Test
    @DisplayName("목록을 다시 보내면 통째로 교체된다")
    void 질문_교체() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(questions(sessionId, List.of("첫 질문", "둘째 질문", "셋째 질문")))
                .andExpect(status().isOk());

        // 편집·삭제·순서변경이 전부 "목록 다시 보내기" 하나로 처리된다.
        mockMvc.perform(questions(sessionId, List.of("셋째 질문", "고친 첫 질문")))
                .andExpect(jsonPath("$.questions.length()").value(2))
                .andExpect(jsonPath("$.questions[0]").value("셋째 질문"))
                .andExpect(jsonPath("$.questions[1]").value("고친 첫 질문"));
    }

    @Test
    @DisplayName("빈 배열을 보내면 전부 지워진다")
    void 질문_비우기() throws Exception {
        long sessionId = startAndGetId();
        mockMvc.perform(questions(sessionId, List.of("질문"))).andExpect(status().isOk());

        mockMvc.perform(questions(sessionId, List.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(0));
    }

    @Test
    @DisplayName("4개를 보내면 400이다")
    void 질문_개수_초과() throws Exception {
        long sessionId = startAndGetId();

        // 화면이 3개다.
        mockMvc.perform(questions(sessionId, List.of("1", "2", "3", "4")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("빈 질문이 섞이면 400이다")
    void 질문_빈_항목() throws Exception {
        long sessionId = startAndGetId();

        mockMvc.perform(questions(sessionId, List.of("괜찮은 질문", "   ")))
                .andExpect(status().isBadRequest());
    }

    // ---------- 공통 ----------

    @Test
    @DisplayName("문답이 끝난 뒤에도 3·4단계를 쓸 수 있다")
    void 종료_후에도_기록() throws Exception {
        long sessionId = startAndGetId();

        // 스텁은 4번째 답변에서 끝낸다.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/sessions/" + sessionId + "/messages")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new SendMessageRequest("답변 " + i, null, null, null))))
                    .andExpect(status().isOk());
        }

        // 3·4단계는 문답이 끝난 뒤 화면이다. COMPLETED 라고 막으면 정상 흐름이 막힌다.
        mockMvc.perform(severity(sessionId, 3, "꽤 아파요"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.severity.level").value(3));

        mockMvc.perform(questions(sessionId, List.of("끝나고 적은 질문")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0]").value("끝나고 적은 질문"));
    }

    @Test
    @DisplayName("남의 세션에는 쓸 수 없고, 있는지조차 알려주지 않는다")
    void 남의_세션() throws Exception {
        long sessionId = startAndGetId();

        given(kakaoClient.resolveKakaoId(anyString())).willReturn(5544332211L);
        String otherToken = "Bearer " + login().accessToken();

        mockMvc.perform(put("/api/sessions/" + sessionId + "/severity")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeverityRequest(3, null))))
                // 403 이 아니라 404 다. 403 은 "그 세션이 있다"는 것을 알려준다.
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new QuestionsRequest(List.of("남의 세션에 끼어들기")))))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.RequestBuilder severity(
            long sessionId, int level, String label) throws Exception {
        return put("/api/sessions/" + sessionId + "/severity")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SeverityRequest(level, label)));
    }

    private org.springframework.test.web.servlet.RequestBuilder questions(
            long sessionId, List<String> items) throws Exception {
        return put("/api/sessions/" + sessionId + "/questions")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new QuestionsRequest(items)));
    }

    private long startAndGetId() throws Exception {
        String body = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:032", null, null, "아랫배"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
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
