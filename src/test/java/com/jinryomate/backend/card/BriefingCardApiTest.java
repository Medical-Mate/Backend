package com.jinryomate.backend.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import com.jinryomate.backend.card.dto.CardDtos.TextFieldRequest;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
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
                                new StartSessionRequest(List.of("hand_finger_joint_R"), "손가락 관절(오른손)"))))
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
                                new StartSessionRequest(List.of("hand_finger_joint_R"), "손가락 관절(오른손)"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.siteText").value("손가락 관절(오른손)"))
                .andExpect(jsonPath("$.siteCodes[0]").value("hand_finger_joint_R"))
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
        assertThat(card.path("meta").path("pipelineVersion").asText()).isNotBlank();
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
    @DisplayName("제목에 진단명을 넣으면 환자가 넣어도 막힌다")
    void 수정에도_검증_적용() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "류마티스", null, null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("증상 정리"))
                .andExpect(jsonPath("$.rejectedFields[0]").value("title(진단명)"));
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
                                "손가락 경직·부종",
                                new TextFieldRequest(FieldStatus.KNOWN, "3주 전 시작"),
                                null, null, null, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("손가락 경직·부종"))
                .andExpect(jsonPath("$.onset.text").value("3주 전 시작"));
    }

    @Test
    @DisplayName("확정된 카드를 고치면 원본은 그대로 두고 새 버전이 생긴다")
    void 확정_후_수정은_새_버전() throws Exception {
        completeOnboarding();
        long cardId = generateCard(startSession()).path("cardId").asLong();

        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String updated = mockMvc.perform(patch("/api/cards/" + cardId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateCardRequest(
                                "바뀐 제목", null, null, null, null, null))))
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
                .andExpect(jsonPath("$.title").value("손가락 관절(오른손) 증상"));
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

    // ---------- helpers ----------

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
                                new StartSessionRequest(List.of("hand_finger_joint_R"), "손가락 관절(오른손)"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("sessionId").asLong();
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
