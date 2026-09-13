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
import com.jinryomate.backend.appointment.entity.Origin;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
                                "서울OO병원", "내과", "재진",
                                LocalDate.of(2026, 9, 12), LocalTime.of(10, 30),
                                null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.department").value("내과"))
                .andExpect(jsonPath("$.purpose").value("재진"))
                .andExpect(jsonPath("$.scheduledOn").value("2026-09-12"))
                .andExpect(jsonPath("$.scheduledTime").value("10:30:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                // 안 보내면 환자가 손으로 만든 일정이다.
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    @DisplayName("시각 없이 날짜만으로 일정을 만들 수 있다")
    void 시간_미정() throws Exception {
        // 시안 1r-2-A 의 "재방문 예정 / 시간 정하고 확정하기". 날짜는 아는데
        // 시각은 아직 모르는 상태가 화면에 있다.
        String body = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null,
                                LocalDate.of(2026, 9, 12), null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledOn").value("2026-09-12"))
                .andExpect(jsonPath("$.scheduledTime").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("appointmentId").asLong();

        // 나중에 시각을 정한다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, LocalTime.of(14, 0), false,
                                null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTime").value("14:00:00"));
    }

    @Test
    @DisplayName("clearTime으로 시각을 다시 미정으로 되돌린다")
    void 시각_지움() throws Exception {
        long id = create("서울OO병원", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));

        // scheduledTime: null 은 "안 바꿈"이다. 지우려면 clearTime 을 써야 한다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, false, null, null, null))))
                .andExpect(jsonPath("$.scheduledTime").value("10:30:00"));

        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, true, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduledTime").doesNotExist());
    }

    @Test
    @DisplayName("진료 후 기록에서 만든 일정은 origin으로 구별된다")
    void 재방문_출처() throws Exception {
        // 시안 1r-2-A 가 "진료 후 기록에서 자동으로 만들었어요" 를 찍는다.
        // 손으로 만든 일정과 구별이 안 되면 그 문구를 못 그린다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, LocalDate.of(2026, 9, 12), null,
                                null, Origin.VISIT_FOLLOW_UP, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("VISIT_FOLLOW_UP"));
    }

    @Test
    @DisplayName("카드를 연결하면 제목이 함께 온다")
    void 카드_연결() throws Exception {
        long cardId = createCard();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", "내과", "재진",
                                LocalDate.of(2026, 9, 12), LocalTime.of(10, 30),
                                List.of(cardId), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].cardId").value((int) cardId))
                // 화면의 "복부 통증 브리핑 카드"
                .andExpect(jsonPath("$.cards[0].title").exists());
    }

    @Test
    @DisplayName("한 일정에 카드를 여러 장 붙일 수 있다")
    void 카드_여러_장() throws Exception {
        // 화면 1r-4-B 가 체크박스이고 개수를 찍는다. 한 장만 붙게 해 두면
        // 두 증상을 한 진료에 들고 가는 경로가 막힌다.
        long first = createCard();
        long second = createCard();

        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, LocalDate.of(2026, 9, 12), null,
                                List.of(first, second), null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(2));
    }

    @Test
    @DisplayName("같은 카드를 두 진료에 가져갈 수 있다")
    void 카드_하나에_진료_여럿() throws Exception {
        // 같은 증상으로 재방문하면 카드를 다시 만들지 않고 이어서 쓴다.
        // 카드 쪽에 appointment_id 를 뒀다면 이게 막힌다.
        long cardId = createCard();

        createWithCards("1차 진료", LocalDate.now(KST).plusDays(2), List.of(cardId));
        createWithCards("2차 진료", LocalDate.now(KST).plusDays(30), List.of(cardId));

        mockMvc.perform(get("/api/me/appointments/upcoming").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cards[0].cardId").value((int) cardId))
                .andExpect(jsonPath("$[1].cards[0].cardId").value((int) cardId));
    }

    @Test
    @DisplayName("cardIds를 보내면 통째로 갈아끼우고, 빈 목록이면 전부 뗀다")
    void 카드_연결_해제() throws Exception {
        long cardId = createCard();
        long id = createWithCards("서울OO병원", LocalDate.of(2026, 9, 12), List.of(cardId));

        // cardIds: null 은 "안 바꿈"이다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, false, null, null, null))))
                .andExpect(jsonPath("$.cards.length()").value(1));

        // 빈 목록은 "전부 뗌"이다. 체크를 다 풀었는데 남으면 안 된다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, false, null, List.of(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    @DisplayName("카드 상세가 진료받을 병원과 진료받은 병원을 나눠서 준다")
    void 카드의_두_병원() throws Exception {
        long cardId = createCard();

        // 예약은 A 병원에 잡는다. 시안 1e-1 의 "진료받을 병원".
        createWithCards("서울OO병원", LocalDate.now(KST).plusDays(7), List.of(cardId));

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.appointment.scheduledOn").exists())
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

        createWithCards("지난 진료", LocalDate.now(KST).minusDays(3), List.of(cardId));
        createWithCards("먼 진료", LocalDate.now(KST).plusDays(30), List.of(cardId));
        createWithCards("가까운 진료", LocalDate.now(KST).plusDays(2), List.of(cardId));

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
                                "서울OO병원", null, null,
                                LocalDate.of(2026, 9, 12), LocalTime.of(10, 30), null, null,
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
                                null, null, null, null, null, false, null, null,
                                List.of(new TodoRequest("달라진 증상 있으면 카드 수정", true))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todos.length()").value(1))
                .andExpect(jsonPath("$.todos[0].done").value(true));

        // 다시 읽어도 남아 있어야 한다. 앱을 껐다 켜는 것과 같다.
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
                                "서울OO병원", null, null,
                                LocalDate.of(2026, 9, 12), LocalTime.of(10, 30), null, null,
                                List.of(new TodoRequest("보험 서류 챙기기", false))))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(body).path("appointmentId").asLong();

        // null 은 "안 바꿈"이다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, "정형외과", null, null, null, false, null, null, null))))
                .andExpect(jsonPath("$.todos.length()").value(1));

        // 빈 목록은 "전부 지움"이다. 환자가 마지막 줄을 지웠는데 남으면 안 된다.
        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, false, null, null, List.of()))))
                .andExpect(jsonPath("$.todos.length()").value(0));
    }

    @Test
    @DisplayName("병원명이 없으면 400")
    void 병원명_필수() throws Exception {
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "  ", null, null,
                                LocalDate.of(2026, 9, 12), LocalTime.of(10, 30), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("예정 날짜가 없으면 400")
    void 날짜_필수() throws Exception {
        // 시각은 빼도 되지만 날짜는 안 된다. 날짜가 없으면 캘린더에 그릴 자리가 없다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, null, LocalTime.of(10, 30), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("필수는 병원명과 날짜뿐이고 나머지는 비워도 된다")
    void 나머지는_선택() throws Exception {
        // 캘린더에서 급히 적는 경로다. 진료과·목적·시각까지 강제하면 그 자리에서 막힌다.
        // 필수를 늘리려면 이 테스트가 먼저 깨져야 한다.
        mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                "서울OO병원", null, null, LocalDate.of(2026, 9, 12), null,
                                null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.department").doesNotExist())
                .andExpect(jsonPath("$.purpose").doesNotExist())
                .andExpect(jsonPath("$.scheduledTime").doesNotExist())
                .andExpect(jsonPath("$.cards.length()").value(0));
    }

    @Test
    @DisplayName("월별 조회는 그 달 것만 가져온다")
    void 월별_조회() throws Exception {
        create("9월 병원", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));
        create("10월 병원", LocalDate.of(2026, 10, 1), LocalTime.of(9, 0));

        mockMvc.perform(get("/api/me/appointments?year=2026&month=9").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("9월 병원"));
    }

    @Test
    @DisplayName("자정 직후 일정도 그날로 잡힌다")
    void 날짜_경계() throws Exception {
        // 예전에는 Instant 하나로 두고 KST 로 잘랐다. 그때는 9월 12일 00:30 이
        // UTC 기준으로 11일이라 하루 밀렸다. 날짜를 DATE 로 두면서 그 계산이 사라졌다.
        create("자정 직후", LocalDate.of(2026, 9, 12), LocalTime.of(0, 30));

        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("자정 직후"));
    }

    @Test
    @DisplayName("일자별 조회는 그 하루만 가져온다")
    void 일자별_조회() throws Exception {
        create("당일", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));
        create("다음날", LocalDate.of(2026, 9, 13), LocalTime.of(10, 30));

        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("당일"));
    }

    @Test
    @DisplayName("같은 날이면 시각이 이른 것이 먼저, 시간 미정은 뒤로 간다")
    void 같은_날_정렬() throws Exception {
        LocalDate day = LocalDate.of(2026, 9, 12);
        create("미정", day, null);
        create("오후", day, LocalTime.of(15, 0));
        create("오전", day, LocalTime.of(9, 0));

        // 시간 미정을 먼저 두면 "오늘 첫 진료"가 틀린다.
        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clinicName").value("오전"))
                .andExpect(jsonPath("$[1].clinicName").value("오후"))
                .andExpect(jsonPath("$[2].clinicName").value("미정"));
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
        create("지난 진료", LocalDate.now(KST).minusDays(3), null);
        long canceled = create("취소한 진료", LocalDate.now(KST).plusDays(1), null);
        create("먼 진료", LocalDate.now(KST).plusDays(30), null);
        create("가까운 진료", LocalDate.now(KST).plusDays(5), null);

        mockMvc.perform(patch("/api/me/appointments/" + canceled)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, null, null, null, null, false, Status.CANCELED, null, null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/appointments/upcoming").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clinicName").value("가까운 진료"))
                .andExpect(jsonPath("$[1].clinicName").value("먼 진료"));
    }

    @Test
    @DisplayName("오늘 잡힌 진료는 시각이 지나도 그날 하루는 남는다")
    void 오늘은_포함() throws Exception {
        // 시각까지 견주면 오전 10시 진료가 10시 1분에 목록에서 사라진다.
        create("오늘 진료", LocalDate.now(KST), LocalTime.of(0, 1));

        mockMvc.perform(get("/api/me/appointments/upcoming").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clinicName").value("오늘 진료"));
    }

    @Test
    @DisplayName("보낸 필드만 바뀐다")
    void 부분_수정() throws Exception {
        long id = create("서울OO병원", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));

        mockMvc.perform(patch("/api/me/appointments/" + id)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAppointmentRequest(
                                null, "정형외과", null, null, null, false, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.department").value("정형외과"))
                // 안 보낸 필드는 그대로다.
                .andExpect(jsonPath("$.clinicName").value("서울OO병원"))
                .andExpect(jsonPath("$.scheduledOn").value("2026-09-12"));
    }

    @Test
    @DisplayName("삭제하면 목록에서 사라진다")
    void 삭제() throws Exception {
        long id = create("서울OO병원", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));

        mockMvc.perform(delete("/api/me/appointments/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/appointments?date=2026-09-12").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("남의 일정과 남의 카드는 건드릴 수 없다")
    void 소유권() throws Exception {
        long cardId = createCard();
        long id = create("서울OO병원", LocalDate.of(2026, 9, 12), LocalTime.of(10, 30));
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
                                "남의 병원", null, null,
                                LocalDate.of(2026, 9, 12), null, List.of(cardId), null, null))))
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

    private long create(String clinicName, LocalDate on, LocalTime at) throws Exception {
        return createAppointment(clinicName, on, at, null);
    }

    private long createWithCards(String clinicName, LocalDate on, List<Long> cardIds)
            throws Exception {
        return createAppointment(clinicName, on, null, cardIds);
    }

    private long createAppointment(String clinicName, LocalDate on, LocalTime at,
                                   List<Long> cardIds) throws Exception {
        String body = mockMvc.perform(post("/api/me/appointments")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAppointmentRequest(
                                clinicName, null, null, on, at, cardIds, null, null))))
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
