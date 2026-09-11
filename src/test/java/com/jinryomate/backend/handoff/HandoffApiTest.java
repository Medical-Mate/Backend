package com.jinryomate.backend.handoff;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * 진료실 전달 (화면 1f).
 *
 * <p>환자가 자기 화면을 의사에게 보여준다. 공유 링크는 새 와이어프레임에서 보류가 되어 뺐다.
 */
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

    @Test
    @DisplayName("확정하지 않은 카드는 의사에게 보여줄 수 없다")
    void 미확정_카드_거부() throws Exception {
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
                // 8축이 전부 자리를 차지한다. 아직 안 물어본 축도 not_asked 로 남아야
                // 의사가 "안 물어본 것"과 "모른다고 한 것"을 구별한다.
                .andExpect(jsonPath("$.axes.onset.status").exists())
                .andExpect(jsonPath("$.axes.severity.status").value("NOT_ASKED"))
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
                .andExpect(jsonPath("$.completeness").doesNotExist())
                // evidence 는 반대로 남긴다. 의사가 "정말 저렇게 말했나"를 확인하는 근거라
                // 내부 추적값이 아니라 진료실에서 쓰는 값이다.
                .andExpect(jsonPath("$.axes.site.evidence").exists())
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

    @Test
    @DisplayName("남의 카드는 열 수 없고, 토큰이 없으면 401이다")
    void 접근_제어() throws Exception {
        long cardId = confirmedCard();
        String otherToken = loginAsOther();

        mockMvc.perform(get("/api/cards/" + cardId + "/handoff").header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/" + cardId + "/handoff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("공유 링크 경로는 더 이상 열리지 않는다")
    void 공유_링크_제거됨() throws Exception {
        // 예전에는 인증 없이 열리던 경로다. 개방을 걷어냈으므로 이제 시큐리티에서 막힌다.
        // 핸들러가 없어 404 가 아니라, 인증 요구에 먼저 걸려 401 이 된다.
        mockMvc.perform(get("/s/AAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isUnauthorized());

        // 토큰이 있어도 핸들러가 없다.
        mockMvc.perform(get("/api/me/share-links").header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

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
