package com.jinryomate.backend.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.jinryomate.backend.card.dto.CardDtos.AxisEdit;
import com.jinryomate.backend.card.dto.CardDtos.ClinicRequest;
import com.jinryomate.backend.card.dto.CardDtos.GenerateCardRequest;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.Side;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
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
 * 문답 세션과 브리핑 카드 통합 테스트.
 *
 * <p>AI는 스텁이라 카드 내용은 가짜지만, 상태 전이·검증·버전 관리는 실제 경로로 돈다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BriefingCardApiTest {

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
    @DisplayName("나이·성별이 없으면 문답을 시작할 수 없다")
    void 프로필_없으면_세션_거부() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:072", null, Side.RIGHT, "손(오른쪽)"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("온보딩을 마치면 부위와 함께 문답을 시작할 수 있다")
    void 세션_시작() throws Exception {
        completeOnboarding();

        mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest("SUR:072", null, Side.RIGHT, "손(오른쪽)"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.siteText").value("손(오른쪽)"))
                .andExpect(jsonPath("$.siteNodeId").value("SUR:072"))
                .andExpect(jsonPath("$.side").value("RIGHT"))
                // 화면의 "2 / 4" 가 아니라 대화 턴 상한이다. 그 넷은 화면 단계고 앱이 안다.
                .andExpect(jsonPath("$.progress.total").value(20))
                // 세션을 만들면 AI 첫 질문이 이미 들어 있다.
                .andExpect(jsonPath("$.messages.length()").value(1));
    }

    @Test
    @DisplayName("카드를 만들면 환자 인적사항이 그 시점 값으로 박힌다")
    void 카드_생성() throws Exception {
        completeOnboarding();
        long sessionId = startSession();

        JsonNode card = generateCard(sessionId);

        assertThat(card.path("status").asText()).isEqualTo("DRAFT");
        assertThat(card.path("version").asInt()).isEqualTo(1);
        assertThat(card.path("patient").path("name").asText()).isEqualTo("김서연");
        assertThat(card.path("patient").path("sex").asText()).isEqualTo("FEMALE");
        // 어떤 파이프라인이 만들었는지 남아야 추적이 된다.
        assertThat(card.path("meta").path("promptVersion").asText()).isNotBlank();
    }

    @Test
    @DisplayName("병원을 고르고 카드를 만들면 카드가 그 병원을 들고 다닌다")
    void 카드의_병원() throws Exception {
        // 시안 1m-B 의 버튼이 "브리핑 카드 만들기" 다. 병원을 고른 다음에 카드를 만든다.
        completeOnboarding();
        long sessionId = startSession();

        String body = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GenerateCardRequest(
                                new ClinicRequest("서울OO병원 내과",
                                        "서울 관악구 남부순환로 1820, 3층")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinic.name").value("서울OO병원 내과"))
                .andExpect(jsonPath("$.clinic.address").value("서울 관악구 남부순환로 1820, 3층"))
                .andReturn().getResponse().getContentAsString();
        long cardId = objectMapper.readTree(body).path("cardId").asLong();

        // 며칠 뒤 기록 탭에서 다시 열어도 남아 있어야 한다. 그게 저장하는 이유다.
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.clinic.name").value("서울OO병원 내과"));

        // 1r-4-B 의 "09.04 작성 · 서울OO병원 내과" 는 목록에서 그린다.
        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(jsonPath("$[0].clinic.name").value("서울OO병원 내과"));

        // 1e-1 의 "변경".
        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                null, null, null, null,
                                new ClinicRequest("OO정형외과의원", "서울 관악구 신림로 245, 5층")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinic.name").value("OO정형외과의원"));
    }

    @Test
    @DisplayName("병원을 건너뛰어도 카드가 만들어진다")
    void 병원_미정() throws Exception {
        // 1m-B 에 "아직 정하지 않았다면 건너뛰어도 돼요" 가 있다. 정상 상태다.
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isOk())
                // 화면은 이때 "병원 미정" 으로 찍는다.
                .andExpect(jsonPath("$.clinic.name").doesNotExist());

        // 본문을 안 보내도 돈다. 기존 호출이 그대로 살아야 한다.
        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(jsonPath("$[0].clinic.name").doesNotExist());
    }

    @Test
    @DisplayName("병원을 안 보내면 이미 정한 병원이 안 바뀐다")
    void 병원_안_보내면_유지() throws Exception {
        completeOnboarding();
        long sessionId = startSessionWithUtterance();

        String body = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GenerateCardRequest(
                                new ClinicRequest("서울OO병원 내과", "서울 관악구 …")))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long cardId = objectMapper.readTree(body).path("cardId").asLong();

        // 카드 수정은 "보낸 것만 바뀐다"가 규칙이다.
        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "고친 설명", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinic.name").value("서울OO병원 내과"));

        // 확정 뒤 새 버전에도 따라간다.
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "또 고친 설명", null, null, null, null))))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.clinic.name").value("서울OO병원 내과"));
    }

    @Test
    @DisplayName("병원명 없이 주소만 보내면 400")
    void 병원명_필수() throws Exception {
        completeOnboarding();
        long sessionId = startSession();

        mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clinic": {"address": "서울 관악구 …"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("카드를 지우면 문답까지 사라진다")
    void 카드_삭제() throws Exception {
        // 증상 대화가 남아 있는데 카드만 지우면, 환자는 지웠다고 생각하면서
        // 증상·복용약이 계속 보관된다.
        completeOnboarding();
        long sessionId = startSessionWithUtterance();
        long cardId = generateCard(sessionId).path("cardId").asLong();

        mockMvc.perform(delete("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sessions/" + sessionId).header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("확정한 카드를 지우면 버전 전체가 사라진다")
    void 버전_체인_삭제() throws Exception {
        // 환자에게는 한 장이고 버전은 서버 사정이다. 하나만 지우면 목록에 나머지가 남는다.
        completeOnboarding();
        long cardId = generateCard(startSessionWithUtterance()).path("cardId").asLong();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());

        // 확정본을 고치면 새 버전이 생긴다.
        String updated = mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "고친 설명", null, null, null, null))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long newCardId = objectMapper.readTree(updated).path("cardId").asLong();

        // 새 버전을 지우면 원본도 함께 사라진다.
        mockMvc.perform(delete("/api/cards/" + newCardId).header("Authorization", token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("남의 카드는 지울 수 없다")
    void 남의_카드_삭제() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSessionWithUtterance()).path("cardId").asLong();

        given(kakaoClient.resolveKakaoId(anyString())).willReturn(9911223344L);
        String other = "Bearer " + login().accessToken();

        // 존재 자체를 알려주지 않는다. 403 이 아니라 404 다.
        mockMvc.perform(delete("/api/cards/" + cardId).header("Authorization", other))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("통증 강도가 카드의 severity 축에 실린다")
    void 강도_축() throws Exception {
        // 강도는 문답이 끝난 뒤 화면이라 AI 로 보낼 길이 없다. 종료 뒤 selections 는
        // 카드를 건드리지 않는다(실측). 그대로 두면 영영 안 들어가서 우리가 채운다.
        completeOnboarding();
        long sessionId = startSessionWithUtterance();

        mockMvc.perform(put("/api/sessions/" + sessionId + "/severity")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":3,\"label\":\"꽤 아파요\"}"))
                .andExpect(status().isOk());

        long cardId = generateCard(sessionId).path("cardId").asLong();

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.axes.severity.status").value("FILLED"))
                .andExpect(jsonPath("$.axes.severity.value").value("3 (꽤 아파요)"))
                // 계약의 selection 규약을 그대로 따른다. 우리가 만든 규칙이 아니다.
                .andExpect(jsonPath("$.axes.severity.source").value("SELECTION"))
                .andExpect(jsonPath("$.axes.severity.evidence[0]").value("[선택] 3 (꽤 아파요)"));
    }

    @Test
    @DisplayName("강도를 안 고르면 severity 축은 비어 있다")
    void 강도_없으면_빈_축() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSessionWithUtterance()).path("cardId").asLong();

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.axes.severity.status").value("NOT_ASKED"));
    }

    @Test
    @DisplayName("알레르기가 카드에 실린다")
    void 알레르기_스냅샷() throws Exception {
        // 시안의 카드는 이 값을 경고 면 맨 위에 올린다("처방 전에 꼭 확인해 주세요").
        // 의사에게 보여주는 한 장이 안전 정보를 얻으려고 API 를 두 번 부르게 하면 안 된다.
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.allergies.status").value("UNKNOWN"));

        // 의사가 보는 화면에도 실려야 한다.
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(jsonPath("$.patient.allergies.status").value("UNKNOWN"));
    }

    @Test
    @DisplayName("프로필의 알레르기를 나중에 고쳐도 이미 만든 카드는 그대로다")
    void 알레르기도_스냅샷이다() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        // "잘 모르겠어요" 였던 것을 "없어요" 로 바꾼다.
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "김서연", 1994, "03-03", Sex.FEMALE,
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜")),
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest(
                                        FieldStatus.NONE, null)))))
                .andExpect(status().isOk());

        // 의사가 본 카드는 그대로여야 한다.
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.allergies.status").value("UNKNOWN"));
    }

    @Test
    @DisplayName("복용약·기저질환이 카드에 실리고 세 값이 구별된다")
    void 복용약_기저질환_스냅샷() throws Exception {
        // 시안 1e-1 의 KV 줄이다. 진료실에서 의사가 가장 먼저 묻는 둘이라,
        // 이 한 장을 보여주면서 다른 화면을 또 열게 하면 안 된다.
        completeOnboarding();  // 복용약 KNOWN ["이부프로펜"] · 기저질환 NONE []
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.medications.status").value("KNOWN"))
                .andExpect(jsonPath("$.patient.medications.items[0]").value("이부프로펜"))
                // "없어요"와 "잘 모르겠어요"가 뭉개지면 처방에서 전혀 다른 말이 된다.
                .andExpect(jsonPath("$.patient.conditions.status").value("NONE"))
                .andExpect(jsonPath("$.patient.conditions.items.length()").value(0));

        // 의사가 보는 화면에도 실려야 한다.
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(jsonPath("$.patient.medications.items[0]").value("이부프로펜"))
                .andExpect(jsonPath("$.patient.conditions.status").value("NONE"));
    }

    @Test
    @DisplayName("프로필의 복용약을 나중에 고쳐도 이미 만든 카드는 그대로다")
    void 복용약도_스냅샷이다() throws Exception {
        // 9월에 만든 카드를 12월에 열었을 때 그때 먹는 약이 붙으면 안 된다.
        // 진료실에서 맞는 값은 "이 카드를 만들 당시 먹던 약"이다.
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "김서연", 1994, "03-03", Sex.FEMALE,
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("나프록센")),
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("고혈압")),
                                new com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest(
                                        FieldStatus.UNKNOWN, null)))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.medications.items[0]").value("이부프로펜"))
                .andExpect(jsonPath("$.patient.conditions.status").value("NONE"));
    }

    @Test
    @DisplayName("확정 후 새 버전을 만들어도 복용약 스냅샷이 따라온다")
    void 새_버전에도_스냅샷이_남는다() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());

        // 확정 이후의 수정은 새 버전으로만 남는다. 그 새 행에도 스냅샷이 있어야 한다.
        String updated = mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                null, null, null, List.of("어제부터 심해졌어요"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andReturn().getResponse().getContentAsString();

        long newCardId = objectMapper.readTree(updated).path("cardId").asLong();
        mockMvc.perform(get("/api/cards/" + newCardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.medications.items[0]").value("이부프로펜"))
                .andExpect(jsonPath("$.patient.conditions.status").value("NONE"));
    }

    @Test
    @DisplayName("프로필을 나중에 고쳐도 이미 만든 카드의 인적사항은 그대로다")
    void 인적사항_스냅샷() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        // 이름을 바꾼다.
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileRequest("김민지"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.patient.name").value("김서연"));
    }

    @Test
    @DisplayName("환자가 넣은 질문도 길이 제한에 걸린다")
    void 수정에도_검증_적용() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                null, null, List.of("가".repeat(41)), null, null))))
                // AI 가 준 값은 unknown 으로 낮춰 저장하지만, 환자가 직접 넣은 값은 400 으로
                // 되돌린다. 환자는 화면에서 바로 고칠 수 있어 조용히 버리면 오히려 혼란스럽다.
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("draft는 제자리에서 수정되고 버전이 그대로다")
    void 초안_수정() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                null,
                                List.of(new AxisEdit("onset", "3주 전 시작")),
                                null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.axes.onset.value").value("3주 전 시작"))
                .andExpect(jsonPath("$.axes.onset.source").value("PATIENT_EDIT"))
                .andExpect(jsonPath("$.axes.onset.evidence[0]").value("[환자 수정] 3주 전 시작"));
    }

    @Test
    @DisplayName("확정된 카드를 고치면 원본은 그대로 두고 새 버전이 생긴다")
    void 확정_후_수정은_새_버전() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSessionWithUtterance()).path("cardId").asLong();

        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String updated = mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "바뀐 증상 설명", null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.parentCardId").value(cardId))
                .andReturn().getResponse().getContentAsString();

        long newCardId = objectMapper.readTree(updated).path("cardId").asLong();
        assertThat(newCardId).isNotEqualTo(cardId);

        // 의사가 본 카드는 그대로 남아야 한다.
        mockMvc.perform(get("/api/cards/" + cardId).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                // 확정본의 본문은 새 버전을 만들어도 바뀌지 않는다.
                .andExpect(jsonPath("$.chiefComplaint").value("오른손이 3주 전부터 저려요"));
    }

    @Test
    @DisplayName("이미 확정된 카드를 다시 확정하면 400")
    void 중복_확정() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 카드는 404")
    void 없는_카드() throws Exception {
        mockMvc.perform(get("/api/cards/999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰 없이 카드를 부르면 401")
    void 무인증_차단() throws Exception {
        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/cards"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("카드 목록은 최근 작성 순이고 본문을 담지 않는다")
    void 목록() throws Exception {
        createCard();
        long recent = createCard();

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].cardId").value((int) recent))
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].createdAt").exists())
                // 증상·복용약이 든 카드를 목록마다 통째로 실어 나를 이유가 없다.
                .andExpect(jsonPath("$[0].onset").doesNotExist())
                .andExpect(jsonPath("$[0].medications").doesNotExist());
    }

    @Test
    @DisplayName("진료 기록이 붙기 전에는 visited가 false다")
    void 목록_진료_전() throws Exception {
        createCard();

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visited").value(false))
                .andExpect(jsonPath("$[0].clinicName").doesNotExist());
    }

    @Test
    @DisplayName("진료 기록이 붙으면 visited가 true가 되고 병원명이 따라온다")
    void 목록_진료_완료() throws Exception {
        long cardId = createCard();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clinicName\":\"서울OO병원 내과\",\"rawNote\":\"피검사 했어요\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visited").value(true))
                .andExpect(jsonPath("$[0].clinicName").value("서울OO병원 내과"));
    }

    @Test
    @DisplayName("병원명이 비어 있어도 진료를 마친 것으로 본다")
    void 목록_병원명_없이_진료_완료() throws Exception {
        // 병원명은 선택 입력이다. clinicName 이 null 이라고 진료 전으로 보면 안 된다.
        long cardId = createCard();
        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawNote\":\"병원 이름은 안 적었어요\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visited").value(true))
                .andExpect(jsonPath("$[0].clinicName").doesNotExist());
    }

    @Test
    @DisplayName("확정한 카드를 고쳐도 목록은 한 장이고, 최신 버전을 가리킨다")
    void 목록은_버전을_접는다() throws Exception {
        // 앱에서 올라온 신고다 — 고칠 때마다 제목·날짜·병원이 똑같은 줄이 한 장씩 늘었다.
        // 버전은 "의사가 본 카드를 보존한다"는 서버 사정이고 환자에게는 한 장이다.
        long v1 = createCard();
        mockMvc.perform(post("/api/cards/" + v1 + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());

        long v2 = editOnce(v1);
        long v3 = editOnce(v2);

        // 세 번 고쳤는데 세 줄이 서면 안 된다.
        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                // 눌러 들어가면 최신이 열려야 한다. 옛 버전을 가리키면 방금 고친 게 안 보인다.
                .andExpect(jsonPath("$[0].cardId").value((int) v3));

        // 홈의 "최근 브리핑 카드"도 같은 CardSummary 라 함께 걸렸다.
        mockMvc.perform(get("/api/me/home").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentCards.length()").value(1))
                .andExpect(jsonPath("$.recentCards[0].cardId").value((int) v3));
    }

    @Test
    @DisplayName("고쳐도 작성 시각이 바뀌지 않는다")
    void 고쳐도_작성일이_그대로다() throws Exception {
        // 최신 행의 createdAt 을 그대로 내면 고칠 때마다 "09.04 작성"이 오늘로 바뀐다.
        // 고친 것이지 새로 쓴 것이 아니다.
        long v1 = createCard();
        mockMvc.perform(post("/api/cards/" + v1 + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());

        String before = objectMapper.readTree(
                        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                                .andReturn().getResponse().getContentAsString())
                .get(0).path("createdAt").asText();

        editOnce(v1);

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(jsonPath("$[0].createdAt").value(before));
    }

    @Test
    @DisplayName("진료 기록이 옛 버전에 붙어 있어도 진료 완료로 본다")
    void 옛_버전에_붙은_기록도_센다() throws Exception {
        // 목록이 최신 버전만 내는데 기록은 v1 에 붙어 있다. 카드 id 로 찾으면 "진료 전"이
        // 되어, 다녀온 진료가 목록에서 사라진다.
        long v1 = createCard();
        mockMvc.perform(post("/api/cards/" + v1 + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/cards/" + v1 + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clinicName\":\"서울OO병원 내과\",\"rawNote\":\"피검사 했어요\"}"))
                .andExpect(status().isOk());

        long v2 = editOnce(v1);

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardId").value((int) v2))
                .andExpect(jsonPath("$[0].visited").value(true))
                .andExpect(jsonPath("$[0].clinicName").value("서울OO병원 내과"));
    }

    @Test
    @DisplayName("문답이 다르면 따로 선다")
    void 다른_문답은_따로_센다() throws Exception {
        // 버전을 접는 기준은 문답이다. 카드를 접는다고 다른 증상까지 합치면 안 된다.
        long first = createCard();
        mockMvc.perform(post("/api/cards/" + first + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        editOnce(first);
        long second = createCard();

        mockMvc.perform(get("/api/me/cards").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                // 나중에 쓴 것이 위다.
                .andExpect(jsonPath("$[0].cardId").value((int) second));
    }

    // ---------- helpers ----------

    /** 확정된 카드를 한 번 고친다. 새 버전 id 를 돌려준다. */
    private long editOnce(long cardId) throws Exception {
        String body = mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"axes": [{"axis": "onset", "value": "지난주부터"}]}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("cardId").asLong();
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

    private void completeOnboarding() throws Exception {
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(profileRequest("김서연"))))
                .andExpect(status().isOk());
    }

    private HealthProfileRequest profileRequest(String name) {
        return new HealthProfileRequest(
                name, 1994, "03-03", Sex.FEMALE,
                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜")),
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest(
                        FieldStatus.UNKNOWN, null));
    }

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

    /**
     * 발화를 한 줄 넣은 세션.
     *
     * <p>카드의 주 호소는 <b>환자가 처음 한 말</b>에서 나온다. 아무 말도 없이 만든 카드는
     * 주 호소가 비는 것이 맞다 — 그 상태를 확인하는 테스트가 아니라면 한 줄은 넣어야 한다.
     */
    private long startSessionWithUtterance() throws Exception {
        long sessionId = startSession();
        mockMvc.perform(post("/api/sessions/" + sessionId + "/messages")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"오른손이 3주 전부터 저려요\"}"))
                .andExpect(status().isOk());
        return sessionId;
    }

    private JsonNode generateCard(long sessionId) throws Exception {
        String body = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** 온보딩부터 카드 생성까지 한 번에. 목록 테스트가 여러 장을 만들 때 쓴다. */
    private long createCard() throws Exception {
        completeOnboarding();
        return generateCard(startSession()).path("cardId").asLong();
    }
}
