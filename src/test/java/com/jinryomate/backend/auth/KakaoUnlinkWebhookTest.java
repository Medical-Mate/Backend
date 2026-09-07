package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카카오 연결 해제 웹훅.
 *
 * <p><b>인증 없이 열리는 경로</b>라 어드민 키 대조가 유일한 방어선이다. 검증이 없으면
 * 누구나 임의의 회원번호로 남을 탈퇴시킬 수 있다. 그 검증이 실제로 막는지 여기서 본다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "kakao.admin-key=test-admin-key")
@Transactional
class KakaoUnlinkWebhookTest {

    private static final long KAKAO_ID = 3344556677L;
    /** {@code application-test.yml} 의 값과 같아야 한다. */
    private static final long APP_ID = 999999L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired HealthProfileRepository profileRepository;

    @MockitoBean KakaoClient kakaoClient;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
    }

    @Test
    @DisplayName("어드민 키가 맞으면 그 회원의 데이터를 지운다")
    void 정상_해제() throws Exception {
        signUpWithProfile();
        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isPresent();
        assertThat(profileRepository.count()).isOne();

        webhook("test-admin-key", APP_ID, KAKAO_ID)
                .andExpect(status().isOk());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    @DisplayName("어드민 키가 틀리면 아무것도 지우지 않는다")
    void 잘못된_키() throws Exception {
        signUpWithProfile();

        // 카카오 규격상 200 을 준다. 오류를 내면 카카오가 웹훅을 비활성화할 수 있다.
        webhook("wrong-key", APP_ID, KAKAO_ID)
                .andExpect(status().isOk());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isPresent();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 아무것도 지우지 않는다")
    void 키_없음() throws Exception {
        signUpWithProfile();

        mockMvc.perform(post("/webhooks/kakao/unlink")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("app_id", String.valueOf(APP_ID))
                        .param("user_id", String.valueOf(KAKAO_ID)))
                .andExpect(status().isOk());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isPresent();
    }

    @Test
    @DisplayName("다른 앱의 이벤트면 아무것도 지우지 않는다")
    void 다른_앱() throws Exception {
        signUpWithProfile();

        // 어드민 키가 여러 앱을 관리할 수 있다. app_id 까지 봐야 우리 사용자다.
        webhook("test-admin-key", 111111L, KAKAO_ID)
                .andExpect(status().isOk());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isPresent();
    }

    @Test
    @DisplayName("없는 회원번호여도 200으로 조용히 넘어간다")
    void 없는_회원() throws Exception {
        // 웹훅이 재전송되거나, 우리가 먼저 탈퇴 처리한 뒤 도착할 수 있다.
        webhook("test-admin-key", APP_ID, 1234567890L)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 웹훅이 두 번 와도 두 번째도 200이다")
    void 멱등() throws Exception {
        signUpWithProfile();

        webhook("test-admin-key", APP_ID, KAKAO_ID).andExpect(status().isOk());
        webhook("test-admin-key", APP_ID, KAKAO_ID).andExpect(status().isOk());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions webhook(
            String adminKey, long appId, long userId) throws Exception {
        return mockMvc.perform(post("/webhooks/kakao/unlink")
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("app_id", String.valueOf(appId))
                .param("user_id", String.valueOf(userId))
                .param("referrer_type", "UNLINK_FROM_APPS"));
    }

    private void signUpWithProfile() throws Exception {
        String body = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readValue(body, TokenResponse.class).accessToken();

        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "김서연", 1994, "03-03", Sex.FEMALE,
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜")),
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new TextFieldRequest(FieldStatus.UNKNOWN, null)))))
                .andExpect(status().isOk());
    }
}
