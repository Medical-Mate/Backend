package com.jinryomate.backend.visit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
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
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.FollowUpRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.LabelsMetaRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.UpdateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitAxisRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/** 진료 후 기록 통합 테스트. 되묻기는 새 와이어프레임에서 빠져 함께 제거했다. */
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
    @DisplayName("기록을 저장하면 항목이 그대로 남는다")
    void 기록_저장() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(fullRecord())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicName").value("○○정형외과"))
                // 칸이 고정이 아니다. 보낸 항목이 보낸 이름 그대로 남는다.
                .andExpect(jsonPath("$.axes.medication_instructions.value")
                        .value("나프록센 500mg·하루 2번 식후"))
                .andExpect(jsonPath("$.axes.findings.value").value("허리 디스크 초기"))
                .andExpect(jsonPath("$.axes.follow_up").doesNotExist())
                // 출처는 서버가 박는다. 앱이 "AI 가 뽑았다"고 주장할 수 없어야 한다.
                .andExpect(jsonPath("$.axes.findings.source").value("PATIENT_EDIT"))
                .andExpect(jsonPath("$.axes.findings.status").value("FILLED"))
                .andExpect(jsonPath("$.followUp.date").value(
                        LocalDate.now().plusDays(14).toString()))
                .andExpect(jsonPath("$.followUp.text").value("2주 뒤"))
                .andExpect(jsonPath("$.followUp.approximate").value(true))
                // isEmpty() 가 empty 필드로 새어 나가면 안 된다. 우리 안에서만 쓰는 판단이다.
                .andExpect(jsonPath("$.followUp.empty").doesNotExist())
                .andExpect(jsonPath("$.patientNotes[0]").value("다음에 올 때 실비보험 서류 챙기기"))
                // 되묻기는 뺐다. 남아 있으면 앱이 없는 화면을 그리려 한다.
                .andExpect(jsonPath("$.checks").doesNotExist())
                .andExpect(jsonPath("$.progress").doesNotExist());
    }

    @Test
    @DisplayName("항목을 고치면 통째로 갈아끼워진다")
    void 항목_수정() throws Exception {
        long cardId = confirmedCard();
        long visitId = createVisitOn(cardId);

        // 환자가 검사 줄을 지우고 약 줄을 고쳤다.
        mockMvc.perform(patch("/api/visits/" + visitId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateVisitRequest(
                                null, null,
                                List.of(new VisitAxisRequest("medication_instructions", "나프록센 250mg")),
                                new FollowUpRequest(LocalDate.now().plusDays(7), "1주 뒤", true),
                                null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.axes.medication_instructions.value").value("나프록센 250mg"))
                // 지운 줄은 사라져야 한다. 병합이면 여기가 남는다.
                .andExpect(jsonPath("$.axes.tests").doesNotExist())
                .andExpect(jsonPath("$.followUp.date").value(LocalDate.now().plusDays(7).toString()))
                .andExpect(jsonPath("$.followUp.text").value("1주 뒤"))
                .andExpect(jsonPath("$.followUp.approximate").value(true))
                // null 인 필드는 건드리지 않는다.
                .andExpect(jsonPath("$.clinicName").value("○○정형외과"))
                .andExpect(jsonPath("$.rawNote").value("피검사 해보자고 하셨어요"));
    }

    @Test
    @DisplayName("메모를 넘기면 항목으로 나눠 돌려준다. 저장은 안 한다")
    void 메모_분류() throws Exception {
        String body = mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyMemoRequest(
                                "선생님이 허리 디스크 초기래요. 나프록센 먹으라고 하셨어요. 다음 주에 오라고 하셨어요.",
                                LocalDate.now(), "○○정형외과", null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentences.length()").value(3))
                // 라벨의 키가 sentences 의 인덱스다. 앱이 이걸로 줄을 옮긴다.
                .andExpect(jsonPath("$.labels").exists())
                .andReturn().getResponse().getContentAsString();

        // 나누기만 하고 저장하지 않는다. 환자가 고치고 나서 저장하기 때문이다.
        mockMvc.perform(get("/api/me/visits").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));

        // 돌려받은 labels 를 그대로 보내면 같은 결과가 나온다 — 모델을 안 부르는 경로다.
        JsonNode first = objectMapper.readTree(body);
        Map<String, String> labels = objectMapper.convertValue(
                first.path("labels"), new TypeReference<Map<String, String>>() {});

        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyMemoRequest(
                                "선생님이 허리 디스크 초기래요. 나프록센 먹으라고 하셨어요. 다음 주에 오라고 하셨어요.",
                                LocalDate.now(), "○○정형외과", labels, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels").value(labels));
    }

    @Test
    @DisplayName("classify=false 면 문장만 나눠 주고 모델을 안 부른다")
    void 문장만_나누기() throws Exception {
        // 온디바이스 1단계. 폰이 분류하기 전에 같은 번호를 보려고 서버에서 문장만 받는다.
        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyMemoRequest(
                                "혈액검사를 받았어요. 2주 뒤에 다시 오세요. 약은 없어요.",
                                LocalDate.now(), "○○정형외과", null, false, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentences.length()").value(3))
                // 아직 아무 축에도 안 들어갔다. 라벨은 폰이 붙인다.
                .andExpect(jsonPath("$.axes.tests.status").value("NOT_ASKED"))
                .andExpect(jsonPath("$.axes.medication_instructions.status").value("NOT_ASKED"))
                .andExpect(jsonPath("$.labels['0']").value("none"))
                // 버리지 않는다. 아직 안 나뉜 문장이 전부 여기 있다.
                .andExpect(jsonPath("$.patientNotes.length()").value(3));
    }

    @Test
    @DisplayName("폰이 붙인 라벨과 폰 모델이 기록에 남는다")
    void 온디바이스_왕복() throws Exception {
        String memo = "혈액검사를 받았어요. 2주 뒤에 다시 오세요. 약은 없어요.";

        // 3단계. 폰이 붙인 라벨로 조립하고, 무엇이 붙였는지도 함께 보낸다.
        String body = mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyMemoRequest(
                                memo, LocalDate.now(), "○○정형외과",
                                Map.of("0", "tests", "1", "follow_up", "2", "medication_instructions"),
                                null,
                                new LabelsMetaRequest("Qwen3-1.7B-Q4_0", "small-v4")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.axes.tests.value").value("혈액검사를 받았어요."))
                .andExpect(jsonPath("$.axes.follow_up.value").value("2주 뒤에 다시 오세요."))
                // 서버 모델이 아니라 폰 모델이 돌아와야 한다.
                .andExpect(jsonPath("$.extractedBy.modelId").value("Qwen3-1.7B-Q4_0"))
                .andExpect(jsonPath("$.extractedBy.promptVersion").value("small-v4"))
                .andReturn().getResponse().getContentAsString();

        // 저장할 때 그대로 옮기면 기록에 남는다. 온디바이스에서는 이게 유일한 추적 수단이다.
        JsonNode classified = objectMapper.readTree(body);
        long cardId = confirmedCard();
        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                "○○정형외과", LocalDate.now(),
                                List.of(new VisitAxisRequest("tests",
                                        classified.path("axes").path("tests").path("value").asText())),
                                null, null, memo,
                                new LabelsMetaRequest("Qwen3-1.7B-Q4_0", "small-v4")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extractedBy.modelId").value("Qwen3-1.7B-Q4_0"))
                .andExpect(jsonPath("$.extractedBy.promptVersion").value("small-v4"));
    }

    @Test
    @DisplayName("폰 모델 정보는 둘 다 있어야 한다")
    void 라벨_출처_검증() throws Exception {
        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClassifyMemoRequest(
                                "혈액검사를 받았어요.", null, null, Map.of("0", "tests"), null,
                                new LabelsMetaRequest("Qwen3-1.7B-Q4_0", "  ")))))
                // AI 는 필드 둘을 다 요구한다. 여기서 막아야 422 대신 제대로 된 400 이 간다.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("빈 메모는 400")
    void 빈_메모() throws Exception {
        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ClassifyMemoRequest("  ", null, null, null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("원문만 적어도 저장된다")
    void 원문만_저장() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                null, null, null, null, null, "피검사 해보자고 하셨어요", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawNote").value("피검사 해보자고 하셨어요"));
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
    @DisplayName("기록 목록은 최근 진료일 순이고 원문을 담지 않는다")
    void 목록() throws Exception {
        long first = confirmedCard();
        createVisitOn(first, LocalDate.of(2026, 7, 15), "OO이비인후과");
        long second = confirmedCard();
        createVisitOn(second, LocalDate.of(2026, 9, 4), "○○정형외과");

        mockMvc.perform(get("/api/me/visits").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].clinicName").value("○○정형외과"))
                .andExpect(jsonPath("$[0].cardTitle").exists())
                .andExpect(jsonPath("$[1].clinicName").value("OO이비인후과"))
                // 증상·복용약이 섞인 긴 텍스트를 목록마다 실어 나를 이유가 없다.
                .andExpect(jsonPath("$[0].rawNote").doesNotExist());
    }

    @Test
    @DisplayName("기록이 없으면 빈 목록이다")
    void 빈_목록() throws Exception {
        mockMvc.perform(get("/api/me/visits").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("카드를 지워도 진료 기록은 남는다")
    void 카드를_지워도_기록은_남는다() throws Exception {
        // 카드는 진료 전에 만든 준비물이고 기록은 진료에서 실제로 들은 것이다.
        // 준비물을 지웠다고 의사에게 들은 말이 사라지면 안 된다.
        long cardId = confirmedCard();
        long visitId = createVisitOn(cardId);

        mockMvc.perform(delete("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/visits/" + visitId).header("Authorization", token))
                .andExpect(status().isOk())
                // 카드 연결은 끊긴다.
                .andExpect(jsonPath("$.cardId").doesNotExist())
                .andExpect(jsonPath("$.clinicName").isNotEmpty());

        // 목록도 줄 제목을 계속 그릴 수 있어야 한다.
        mockMvc.perform(get("/api/me/visits").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardId").doesNotExist())
                .andExpect(jsonPath("$[0].cardTitle").isNotEmpty());
    }

    @Test
    @DisplayName("기록만 지우면 카드는 남는다")
    void 기록만_삭제() throws Exception {
        // 전에는 이 경로가 없어서 기록 삭제가 카드 삭제로 대신 나갔고,
        // 기록 한 건을 지우려던 사용자가 카드까지 잃었다.
        long cardId = confirmedCard();
        long visitId = createVisitOn(cardId);

        mockMvc.perform(delete("/api/visits/" + visitId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/visits/" + visitId).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("남의 기록은 지울 수 없다")
    void 남의_기록_삭제() throws Exception {
        long visitId = createVisitOn(confirmedCard());

        given(kakaoClient.resolveKakaoId(anyString())).willReturn(8877665544L);
        String other = "Bearer " + login().accessToken();

        // 존재 자체를 알려주지 않는다. 403 이 아니라 404 다.
        mockMvc.perform(delete("/api/visits/" + visitId).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 기록은 404, 토큰 없으면 401")
    void 접근_제어() throws Exception {
        mockMvc.perform(get("/api/visits/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/visits/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/visits"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private CreateVisitRequest fullRecord() {
        return new CreateVisitRequest(
                "○○정형외과", LocalDate.now(),
                List.of(new VisitAxisRequest("findings", "허리 디스크 초기"),
                        new VisitAxisRequest("tests", "혈액검사(류마티스 인자 포함)"),
                        new VisitAxisRequest("medication_instructions", "나프록센 500mg·하루 2번 식후")),
                new FollowUpRequest(LocalDate.now().plusDays(14), "2주 뒤", true),
                List.of("다음에 올 때 실비보험 서류 챙기기"),
                "피검사 해보자고 하시고, 결과는 3일 뒤에 나온대요",
                new LabelsMetaRequest("Qwen3-1.7B-Q4_0", "small-v4"));
    }

    private long createVisitOn(long cardId) throws Exception {
        return createVisitOn(cardId, LocalDate.now(), "○○정형외과");
    }

    private long createVisitOn(long cardId, LocalDate visitedOn, String clinic) throws Exception {
        String body = mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                clinic, visitedOn,
                                List.of(new VisitAxisRequest("tests", "혈액검사")),
                                null, null, "피검사 해보자고 하셨어요", null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("visitId").asLong();
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
