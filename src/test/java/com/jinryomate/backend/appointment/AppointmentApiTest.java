package com.jinryomate.backend.appointment;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.CreateAppointmentRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.TodoRequest;
import com.jinryomate.backend.appointment.dto.AppointmentDtos.UpdateAppointmentRequest;
import com.jinryomate.backend.appointment.entity.Appointment.Status;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

/** 캘린더와 진료 예정 일정 (화면 1r · 1n). */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentApiTest {

    private static final long KAKAO_ID = 6677889900L;
    private static final long OTHER_KAKAO_ID = 5544332211L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
    @DisplayName("카드 없이도 일정을 만들 수 있다")
    void 카드_없이_등록() throws Exception {
        // 캘린더의 + 버튼이 이 경로다. 카드에서 출발하지 않는다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", "내과", "재진", kst(2026, 9, 12, 10, 30), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.department").value("내과"))
                .andExpect(jsonPath("$.purpose").value("재진"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.cardId").doesNotExist());
    }

    @Test
    @DisplayName("카드를 연결하면 제목이 함께 온다")
    void 카드_연결() throws Exception {
        long cardId = createCard();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", "내과", "재진", kst(2026, 9, 12, 10, 30), cardId, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value((int) cardId))
                // 화면의 "복부 통증 브리핑 카드"
                .andExpect(jsonPath("$.cardTitle").exists());
    }

    @Test
    @DisplayName("카드 상세가 진료받을 병원과 진료받은 병원을 나눠서 준다")
    void 카드의_두_병원() throws Exception {
        long cardId = createCard();

        // 예약은 A 병원에 잡는다. 시안 1e-1 의 "진료받을 병원".
        createWithCard("서울OO병원", Instant.now().plus(7, ChronoUnit.DAYS), cardId);

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.clinicName").value("서울OO병원"))
                // 아직 진료 전이라 받은 병원은 없다.
                .andExpect(jsonPath("$.visit").doesNotExist());

        // 실제로는 B 병원에 갔다. 둘이 다를 수 있어서 이름을 나눈다.
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clinicName": "○○정형외과", "visitedOn": "2026-09-13"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.appointment.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.visit.clinicName").value("○○정형외과"))
                .andExpect(jsonPath("$.visit.visitedOn").value("2026-09-13"));
    }

    @Test
    @DisplayName("일정이 여럿이면 아직 안 지난 것 중 가장 가까운 것을 준다")
    void 카드의_다음_일정() throws Exception {
        long cardId = createCard();

        createWithCard("지난 진료", Instant.now().minus(3, ChronoUnit.DAYS), cardId);
        createWithCard("먼 진료", Instant.now().plus(30, ChronoUnit.DAYS), cardId);
        createWithCard("가까운 진료", Instant.now().plus(2, ChronoUnit.DAYS), cardId);

        // 환자가 지금 준비하는 진료가 그것이다.
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.appointment.clinicName").value("가까운 진료"));
    }

    @Test
    @DisplayName("진료 전 할 일이 저장되고 체크가 남는다")
    void 할_일() throws Exception {
        // 1r-4 에서 적고 1r-2 에서 체크한다. 자리가 없어서 앱을 다시 켜면 사라졌다.
        String body = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, kst(2026, 9, 12, 10, 30), null,
                                List.of(new TodoRequest("달라진 증상 있으면 카드 수정", false),
                                        new TodoRequest("보험 서류 챙기기", false))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.length()").value(2))
                .andExpect(jsonPath("$.todos[0].text").value("달라진 증상 있으면 카드 수정"))
                .andExpect(jsonPath("$.todos[0].done").value(false))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).path("appointmentId").asLong();

        // 첫 줄에 체크하고 둘째 줄은 지웠다. 목록째 보낸다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, null, false,
                                List.of(new TodoRequest("달라진 증상 있으면 카드 수정", true))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.length()").value(1))
                .andExpect(jsonPath("$.todos[0].done").value(true));

        // 다시 읽어도 남아 있어야 한다. 앱을 껐다 켜는 것과 같다.
        mockMvc.perform(get("/api/me/appointments/upcoming").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/me/appointments?year=2026&month=9").header("Authorization", token))
                .andExpect(jsonPath("$[0].todos[0].done").value(true));
    }

    @Test
    @DisplayName("할 일을 안 보내면 그대로, 빈 목록을 보내면 지워진다")
    void 할_일_안_보냄과_빈_목록은_다르다() throws Exception {
        String body = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, kst(2026, 9, 12, 10, 30), null,
                                List.of(new TodoRequest("보험 서류 챙기기", false))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("appointmentId").asLong();

        // null 은 "안 바꿈"이다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, "정형외과", null, null, null, null, false, null))))
                .andExpect(jsonPath("$.todos.length()").value(1));

        // 빈 목록은 "전부 지움"이다. 환자가 마지막 줄을 지웠는데 남으면 안 된다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, null, false, List.of()))))
                .andExpect(jsonPath("$.todos.length()").value(0));
    }

    @Test
    @DisplayName("병원명이 없으면 400")
    void 병원명_필수() throws Exception {
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "  ", null, null, kst(2026, 9, 12, 10, 30), null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("예정 일시가 없으면 400")
    void 일시_필수() throws Exception {
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("필수는 병원명과 일시뿐이고 나머지는 비워도 된다")
    void 나머지는_선택() throws Exception {
        // 캘린더에서 급히 적는 경로다. 진료과·목적까지 강제하면 그 자리에서 막힌다.
        // 필수를 늘리려면 이 테스트가 먼저 깨져야 한다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, kst(2026, 9, 12, 10, 30), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.purpose").doesNotExist())
                .andExpect(jsonPath("$.cardId").doesNotExist());
    }

    @Test
    @DisplayName("월별 조회는 그 달 것만 가져온다")
    void 월별_조회() throws Exception {
        create("9월 병원", kst(2026, 9, 12, 10, 30));
        create("10월 병원", kst(2026, 10, 1, 9, 0));

        mockMvc.perform(get("/api/me/appointments?year=2026&month=9").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("9월 병원"));
    }

    @Test
    @DisplayName("한국 시각 자정 직후 일정도 그날로 잡힌다")
    void 시간대_경계() throws Exception {
        // UTC 로 자르면 KST 오전 9시 이전이 전날로 밀린다. 9월 12일 00:30 이 대표적이다.
        create("자정 직후", kst(2026, 9, 12, 0, 30));

        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("자정 직후"));
    }

    @Test
    @DisplayName("일자별 조회는 그 하루만 가져온다")
    void 일자별_조회() throws Exception {
        create("당일", kst(2026, 9, 12, 10, 30));
        create("다음날", kst(2026, 9, 13, 10, 30));

        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("당일"));
    }

    @Test
    @DisplayName("date도 year·month도 없으면 400")
    void 조회_파라미터_필수() throws Exception {
        mockMvc.perform(get("/api/me/appointments").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다가오는 일정은 지난 것과 취소된 것을 빼고 가까운 순으로 준다")
    void 다가오는_일정() throws Exception {
        create("지난 진료", Instant.now().minus(3, ChronoUnit.DAYS));
        long canceled = create("취소한 진료", Instant.now().plus(1, ChronoUnit.DAYS));
        create("먼 진료", Instant.now().plus(30, ChronoUnit.DAYS));
        create("가까운 진료", Instant.now().plus(5, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/me/appointments/" + canceled)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, Status.CANCELED, null, false, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/appointments/upcoming").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clinicName").value("가까운 진료"))
                .andExpect(jsonPath("$[1].clinicName").value("먼 진료"));
    }

    @Test
    @DisplayName("보낸 필드만 바뀐다")
    void 부분_수정() throws Exception {
        long id = create("서울OO병원", kst(2026, 9, 12, 10, 30));

        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, "정형외과", null, null, null, null, false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("정형외과"))
                // 안 보낸 필드는 그대로다.
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"));
    }

    @Test
    @DisplayName("clearCard로 카드 연결을 끊는다")
    void 카드_연결_해제() throws Exception {
        long cardId = createCard();
        long id = createWithCard("서울OO병원", kst(2026, 9, 12, 10, 30), cardId);

        // cardId: null 은 "안 바꿈"이다. 끊으려면 clearCard 를 써야 한다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, null, false, null))))
                .andExpect(jsonPath("$.cardId").value((int) cardId));

        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, null, true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").doesNotExist());
    }

    @Test
    @DisplayName("삭제하면 목록에서 사라진다")
    void 삭제() throws Exception {
        long id = create("서울OO병원", kst(2026, 9, 12, 10, 30));

        mockMvc.perform(delete("/api/me/appointments/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("남의 일정과 남의 카드는 건드릴 수 없다")
    void 소유권() throws Exception {
        long cardId = createCard();
        long id = create("서울OO병원", kst(2026, 9, 12, 10, 30));
        String otherToken = loginAsOther();

        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/me/appointments/" + id).header("Authorization", otherToken))
                .andExpect(status().isNotFound());

        // 남의 카드를 자기 일정에 붙일 수 없다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "남의 병원", null, null, kst(2026, 9, 12, 10, 30), cardId, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void 인증_필요() throws Exception {
        mockMvc.perform(get("/api/me/appointments?date=2026-09-12"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/appointments/upcoming"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private Instant kst(int y, int m, int d, int hour, int minute) {
        return LocalDateTime.of(y, m, d, hour, minute).atZone(KST).toInstant();
    }

    private long create(String clinicName, Instant at) throws Exception {
        return createWithCard(clinicName, at, null);
    }

    private long createWithCard(String clinicName, Instant at, Long cardId) throws Exception {
        String body = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clinicName, null, null, at, cardId, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("appointmentId").asLong();
    }

    private String loginAsOther() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(OTHER_KAKAO_ID);
        return "Bearer " + login().accessToken();
    }

    private long createCard() throws Exception {
        completeOnboarding();
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
