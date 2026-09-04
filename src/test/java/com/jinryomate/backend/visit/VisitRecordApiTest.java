package com.jinryomate.backend.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.visit.dto.VisitDtos.AnswerRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import java.time.LocalDate;
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

/** 진료 후 기록과 되묻기 통합 테스트. */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VisitRecordApiTest {

    private static final long KAKAO_ID = 3847562910L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("확정되지 않은 카드에는 기록을 남길 수 없다")
    void 미확정_카드_거부() throws Exception {
        long cardId = createCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullRecord())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("기록을 저장하면 되묻기 문항 3개가 함께 만들어진다")
    void 기록_저장과_문항_생성() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullRecord())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("○○정형외과"))
                .andExpect(jsonPath("$.prescription").value("나프록센 500mg·하루 2번 식후"))
                .andExpect(jsonPath("$.checks.length()").value(3))
                .andExpect(jsonPath("$.progress.total").value(3))
                .andExpect(jsonPath("$.progress.answered").value(0))
                // 답하기 전에 정답이 보이면 되묻기가 의미 없다.
                .andExpect(jsonPath("$.checks[0].correct").doesNotExist());
    }

    @Test
    @DisplayName("받은 약이 없으면 약에 대한 문항을 내지 않는다")
    void 없는_항목은_묻지_않는다() throws Exception {
        long cardId = confirmedCard();

        String body = mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                "○○정형외과", LocalDate.now(), "혈액검사", null, null, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 받은 약도 결과도 없으니 "무엇을 하셨죠?" 하나만 남는다.
        JsonNode checks = objectMapper.readTree(body).path("checks");
        assertThat(checks).hasSize(1);
        assertThat(checks.toString()).doesNotContain("약은");
    }

    @Test
    @DisplayName("원문만 적어도 저장된다")
    void 원문만_저장() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                null, null, null, null, null, "피검사 해보자고 하셨어요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawNote").value("피검사 해보자고 하셨어요"))
                // 정리된 항목이 없으니 물을 것도 없다.
                .andExpect(jsonPath("$.checks.length()").value(0));
    }

    @Test
    @DisplayName("맞게 답하면 correct가 true이고 진행도가 오른다")
    void 정답() throws Exception {
        long visitId = createVisit();
        long checkId = firstCheckId(visitId);

        mockMvc.perform(post("/api/visits/" + visitId + "/checks/" + checkId + "/answer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AnswerRequest("나프록센 하루 2번 식후에 먹어요"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.progress.answered").value(1))
                .andExpect(jsonPath("$.progress.total").value(3));
    }

    @Test
    @DisplayName("틀려도 막지 않고 정정 문구를 돌려준다")
    void 오답도_넘어간다() throws Exception {
        long visitId = createVisit();
        long checkId = firstCheckId(visitId);

        mockMvc.perform(post("/api/visits/" + visitId + "/checks/" + checkId + "/answer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest("어... 한 번인가?"))))
                // 시험이 아니라 이해 확인이다. 200 으로 응답하고 정정만 알려준다.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(false))
                .andExpect(jsonPath("$.correction").value("나프록센 500mg·하루 2번 식후"))
                .andExpect(jsonPath("$.progress.answered").value(1));
    }

    @Test
    @DisplayName("같은 문항에 두 번 답하면 400")
    void 중복_답변() throws Exception {
        long visitId = createVisit();
        long checkId = firstCheckId(visitId);

        answerFirst(visitId, checkId, "네");
        mockMvc.perform(post("/api/visits/" + visitId + "/checks/" + checkId + "/answer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest("다시"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("한 카드에 기록은 하나뿐이다")
    void 중복_기록_차단() throws Exception {
        long cardId = confirmedCard();
        createVisitOn(cardId);

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullRecord())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 기록은 404, 토큰 없으면 401")
    void 접근_제어() throws Exception {
        mockMvc.perform(get("/api/visits/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/visits/1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private CreateVisitRequest fullRecord() {
        return new CreateVisitRequest(
                "○○정형외과", LocalDate.now(),
                "혈액검사(류마티스 인자 포함)",
                "3일 뒤 확인",
                "나프록센 500mg·하루 2번 식후",
                "피검사 해보자고 하시고, 결과는 3일 뒤에 나온대요");
    }

    private long createVisit() throws Exception {
        return createVisitOn(confirmedCard());
    }

    private long createVisitOn(long cardId) throws Exception {
        String body = mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullRecord())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("visitId").asLong();
    }

    private long firstCheckId(long visitId) throws Exception {
        String body = mockMvc.perform(get("/api/visits/" + visitId).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("checks").get(0).path("checkId").asLong();
    }

    private void answerFirst(long visitId, long checkId, String answer) throws Exception {
        mockMvc.perform(post("/api/visits/" + visitId + "/checks/" + checkId + "/answer")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AnswerRequest(answer))))
                .andExpect(status().isOk());
    }

    private long confirmedCard() throws Exception {
        long cardId = createCard();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        return cardId;
    }

    private long createCard() throws Exception {
        completeOnboarding();
        String session = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest(List.of("hand_finger_joint_R"), "손가락 관절(오른손)"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(session).path("sessionId").asLong();

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
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜")),
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
