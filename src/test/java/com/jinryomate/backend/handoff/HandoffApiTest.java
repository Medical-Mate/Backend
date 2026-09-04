package com.jinryomate.backend.handoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.jinryomate.backend.card.repository.BriefingCardRepository;
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

/** 진료실 전달(S4)과 공유 링크(S6) 통합 테스트. */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HandoffApiTest {

    private static final long KAKAO_ID = 3847562910L;
    private static final long OTHER_KAKAO_ID = 1122334455L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BriefingCardRepository cardRepository;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    // ---------- S4 진료실 전달 ----------

    @Test
    @DisplayName("확정하지 않은 카드는 의사에게 보여줄 수 없다")
    void 미확정_카드_전달_거부() throws Exception {
        long cardId = createCard();

        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("확정한 카드를 전달 화면으로 연다")
    void 전달_화면() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.name").value("김서연"))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.onset.status").exists())
                .andExpect(jsonPath("$.confirmedAt").exists());
    }

    @Test
    @DisplayName("전달 화면에는 내부 추적값이 담기지 않는다")
    void 내부값_비노출() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(status().isOk())
                // 의사에게 AI 파이프라인 버전을 보여줄 이유가 없다.
                .andExpect(jsonPath("$.meta").doesNotExist())
                .andExpect(jsonPath("$.evidence").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.rejectedFields").doesNotExist());
    }

    @Test
    @DisplayName("전달하면 전달 시각이 남는다")
    void 전달_시각_기록() throws Exception {
        long cardId = confirmedCard();
        assertThat(cardRepository.findById(cardId).orElseThrow().getHandedOffAt()).isNull();

        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", token))
                .andExpect(status().isOk());

        assertThat(cardRepository.findById(cardId).orElseThrow().getHandedOffAt()).isNotNull();
    }

    // ---------- S6 공유 링크 ----------

    @Test
    @DisplayName("링크를 발급하면 토큰과 주소가 함께 온다")
    void 링크_발급() throws Exception {
        long cardId = confirmedCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/share").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareLinkId").exists())
                // 128비트를 Base64URL 로 적으면 22자다.
                .andExpect(jsonPath("$.token").value(org.hamcrest.Matchers.hasLength(22)))
                .andExpect(jsonPath("$.url").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.viewCount").value(0));
    }

    @Test
    @DisplayName("확정하지 않은 카드로는 링크를 만들 수 없다")
    void 미확정_카드_링크_거부() throws Exception {
        long cardId = createCard();

        mockMvc.perform(post("/api/cards/" + cardId + "/share").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("공유된 카드는 로그인 없이 열린다")
    void 공개_열람() throws Exception {
        String shareToken = issueShareToken(confirmedCard());

        // Authorization 헤더가 없다.
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patient.name").value("김서연"))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    @DisplayName("공유 응답에 내부 식별자가 새지 않는다")
    void 공개_응답_식별자_비노출() throws Exception {
        String shareToken = issueShareToken(confirmedCard());

        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    @DisplayName("열람하면 횟수가 오른다")
    void 열람_기록() throws Exception {
        long cardId = confirmedCard();
        String shareToken = issueShareToken(cardId);

        mockMvc.perform(get("/s/" + shareToken)).andExpect(status().isOk());
        mockMvc.perform(get("/s/" + shareToken)).andExpect(status().isOk());

        mockMvc.perform(get("/api/me/share-links").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewCount").value(2))
                .andExpect(jsonPath("$[0].viewedAt").exists());
    }

    @Test
    @DisplayName("폐기한 링크는 더 이상 열리지 않는다")
    void 폐기() throws Exception {
        long cardId = confirmedCard();
        String body = issueShare(cardId);
        long shareLinkId = objectMapper.readTree(body).path("shareLinkId").asLong();
        String shareToken = objectMapper.readTree(body).path("token").asText();

        mockMvc.perform(delete("/api/share-links/" + shareLinkId).header("Authorization", token))
                .andExpect(status().isNoContent());

        // 폐기·만료·없음을 모두 404 로 묶는다. 구분해 알려주면 토큰 존재가 새어 나간다.
        mockMvc.perform(get("/s/" + shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 토큰도 404다")
    void 없는_토큰() throws Exception {
        mockMvc.perform(get("/s/AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("남의 카드로는 링크를 만들 수 없고 남의 링크는 폐기할 수 없다")
    void 소유권() throws Exception {
        long cardId = confirmedCard();
        long shareLinkId = objectMapper.readTree(issueShare(cardId)).path("shareLinkId").asLong();

        String otherToken = loginAsOther();

        mockMvc.perform(post("/api/cards/" + cardId + "/share").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/share-links/" + shareLinkId).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("전달 화면은 토큰이 있어야 열린다")
    void 인증_필요() throws Exception {
        mockMvc.perform(get("/api/cards/1/handoff"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/share-links"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String issueShare(long cardId) throws Exception {
        return mockMvc.perform(post("/api/cards/" + cardId + "/share").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String issueShareToken(long cardId) throws Exception {
        return objectMapper.readTree(issueShare(cardId)).path("token").asText();
    }

    private String loginAsOther() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(OTHER_KAKAO_ID);
        return "Bearer " + login().accessToken();
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
