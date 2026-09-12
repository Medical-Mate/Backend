package com.jinryomate.backend.home;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.Side;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

/** 홈 요약 (화면 1n). */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomeApiTest {

    private static final long KAKAO_ID = 8899001122L;

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
    @DisplayName("신규 사용자도 200이고 전부 비어 있다")
    void 신규_사용자() throws Exception {
        // 오류가 아니라 정상 상태다. 앱이 이걸 보고 "증상 정리 유도" 화면(1n-2)을 그린다.
        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastVisitedOn").doesNotExist())
                .andExpect(jsonPath("$.nextAppointment").doesNotExist())
                .andExpect(jsonPath("$.inProgressSession").doesNotExist())
                .andExpect(jsonPath("$.recentCards.length()").value(0));
    }

    @Test
    @DisplayName("작성 중이던 문답이 진행도와 함께 온다")
    void 이어서_하기() throws Exception {
        completeOnboarding();
        startSession();

        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                // 화면의 "복부 통증 · 3단계 중 2단계까지 답했어요"가 이 셋으로 만들어진다.
                .andExpect(jsonPath("$.inProgressSession.sessionId").exists())
                .andExpect(jsonPath("$.inProgressSession.siteText").value("손(오른쪽)"))
                .andExpect(jsonPath("$.inProgressSession.progressCurrent").exists())
                .andExpect(jsonPath("$.inProgressSession.progressTotal").exists());
    }

    @Test
    @DisplayName("카드를 만들어 세션이 끝나면 이어서 하기가 사라진다")
    void 완료된_세션은_안_온다() throws Exception {
        createCard();

        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inProgressSession").doesNotExist())
                .andExpect(jsonPath("$.recentCards.length()").value(1));
    }

    @Test
    @DisplayName("최근 카드는 3건까지만 온다")
    void 최근_카드_3건() throws Exception {
        for (int i = 0; i < 4; i++) {
            createCard();
        }

        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                // 화면이 3건을 보여주고 "전체 보기"로 넘어간다.
                .andExpect(jsonPath("$.recentCards.length()").value(3));
    }

    @Test
    @DisplayName("마지막 진료일과 다음 일정이 온다")
    void 진료일과_일정() throws Exception {
        long cardId = createCard();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                "서울OO병원", LocalDate.of(2026, 9, 4), null, null, null, "피검사"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", "내과", "재진",
                                Instant.now().plus(5, ChronoUnit.DAYS), cardId, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastVisitedOn").value("2026-09-04"))
                .andExpect(jsonPath("$.nextAppointment.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.nextAppointment.cardTitle").exists());
    }

    @Test
    @DisplayName("문구와 D-day는 서버가 만들지 않는다")
    void 문구_없음() throws Exception {
        createCard();

        // 말투를 바꿀 때마다 배포해야 하고, 시간대가 어긋나면 날짜 수가 하루 틀린다.
        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayMessage").doesNotExist())
                .andExpect(jsonPath("$.daysSinceLastVisit").doesNotExist())
                .andExpect(jsonPath("$.nextAppointment.dday").doesNotExist());
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void 인증_필요() throws Exception {
        mockMvc.perform(get("/api/me/home"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private long startSession() throws Exception {
        String body = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:072", null, Side.RIGHT, "손(오른쪽)"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
    }

    private long createCard() throws Exception {
        completeOnboarding();
        String card = mockMvc.perform(post("/api/sessions/" + startSession() + "/card")
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
