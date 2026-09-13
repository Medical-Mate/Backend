package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.appointment.repository.AppointmentRepository;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionsRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SeverityRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.Side;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.LabelsMetaRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitAxisRequest;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * 데이터를 다 채운 상태에서 탈퇴한다.
 *
 * <p>{@code AuthApiTest} 의 탈퇴 테스트는 로그인 직후에 지워서 <b>삭제 순서를 건드리지 않는다</b>.
 * 일정은 카드를, 카드는 문답 세션을 참조하므로 순서가 틀리면 외래키에 걸리는데,
 * 빈 사용자로는 그게 드러나지 않는다.
 *
 * <p>도메인이 늘어 {@code withdraw()} 에 줄이 붙을 때마다 여기가 먼저 깨져야 한다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WithdrawCascadeTest {

    private static final long KAKAO_ID = 5566778899L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired HealthProfileRepository profileRepository;
    @Autowired IntakeSessionRepository sessionRepository;
    @Autowired BriefingCardRepository cardRepository;
    @Autowired VisitRecordRepository visitRepository;
    @Autowired AppointmentRepository appointmentRepository;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("프로필·문답·카드·기록·일정이 다 있어도 탈퇴가 끝까지 지운다")
    void 전체_삭제() throws Exception {
        completeOnboarding();

        long cardId = confirmedCard();

        createVisit(cardId);

        // 카드에 매달린 일정이어야 FK 순서가 드러난다. 카드를 먼저 지우면 여기서 걸린다.
        createAppointment(cardId);

        // 세션에 매달린 값 컬렉션. 세션을 지울 때 함께 안 지워지면 외래키에 걸린다.
        recordSeverityAndQuestions();

        // 여기까지가 환자 한 명이 S1~S6 를 다 거친 상태다.
        assertThat(profileRepository.count()).isOne();
        assertThat(cardRepository.count()).isOne();
        assertThat(visitRepository.count()).isOne();
        assertThat(appointmentRepository.count()).isOne();

        mockMvc.perform(delete("/api/me").header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
        assertThat(profileRepository.count()).isZero();
        assertThat(sessionRepository.count()).isZero();
        assertThat(cardRepository.count()).isZero();
        assertThat(visitRepository.count()).isZero();
        assertThat(appointmentRepository.count()).isZero();
    }

    // ---------- helpers ----------

    /**
     * 증상 정리 3·4단계.
     *
     * <p>{@code intake_session_questions} 는 {@code @ElementCollection} 이라 세션과 함께
     * 지워져야 한다. 안 지워지면 세션 삭제가 외래키에 걸린다.
     */
    private void recordSeverityAndQuestions() throws Exception {
        long sessionId = sessionRepository.findAll().getFirst().getId();

        mockMvc.perform(put("/api/sessions/" + sessionId + "/severity")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SeverityRequest(3, "꽤 아파요"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/sessions/" + sessionId + "/questions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionsRequest(
                                List.of("혈액 검사를 받아야 하나요?", "약을 같이 먹어도 되나요?")))))
                .andExpect(status().isOk());
    }

    private void createAppointment(long cardId) throws Exception {
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "○○정형외과", "정형외과", "재진",
                                LocalDate.now().plusDays(7).atStartOfDay(ZoneOffset.UTC).toInstant(),
                                cardId, null))))
                .andExpect(status().isOk());
    }

    private long createVisit(long cardId) throws Exception {
        String body = mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                "○○정형외과", LocalDate.now(),
                                List.of(new VisitAxisRequest("tests", "혈액검사"),
                                        new VisitAxisRequest("medication_instructions", "나프록센 500mg")),
                                null, null, "피검사 해보자고 하셨어요", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("visitId").asLong();
    }

    private long confirmedCard() throws Exception {
        String session = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:072", null, Side.RIGHT, "손(오른쪽)"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(session).path("sessionId").asLong();

        String card = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long cardId = objectMapper.readTree(card).path("cardId").asLong();

        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        return cardId;
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
