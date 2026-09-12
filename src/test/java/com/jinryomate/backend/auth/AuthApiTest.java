package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.RefreshRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.SettingsRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.repository.RefreshTokenRepository;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 API 통합 테스트.
 *
 * <p>카카오 API는 목으로 대체한다. CI에서 외부 호출 없이 돌아야 하고,
 * 실제 카카오 토큰을 테스트에 넣을 수도 없다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

    private static final long KAKAO_ID = 3847562910L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @MockitoBean KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
    }

    @Test
    @DisplayName("처음 로그인하면 가입되고 온보딩이 필요하다고 알려준다")
    void 신규_로그인() throws Exception {
        String body = login("kakao-token").getResponse().getContentAsString();
        TokenResponse response = objectMapper.readValue(body, TokenResponse.class);

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.onboardingRequired()).isTrue();
        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isPresent();
    }

    @Test
    @DisplayName("같은 회원번호로 다시 오면 새로 가입시키지 않는다")
    void 재로그인은_가입하지_않는다() throws Exception {
        login("kakao-token");
        String body = login("kakao-token").getResponse().getContentAsString();
        TokenResponse response = objectMapper.readValue(body, TokenResponse.class);

        assertThat(userRepository.count()).isEqualTo(1);

        // 재로그인이라도 온보딩을 마치지 않았으면 여전히 온보딩이 필요하다.
        // onboardingRequired 는 "신규 가입 여부"가 아니라 "온보딩 완료 여부"를 뜻한다.
        assertThat(response.onboardingRequired()).isTrue();
    }

    @Test
    @DisplayName("refresh 토큰은 한 번 쓰면 막히고 새 토큰이 나온다")
    void refresh_토큰_회전() throws Exception {
        TokenResponse first = objectMapper.readValue(
                login("kakao-token").getResponse().getContentAsString(), TokenResponse.class);

        String refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        TokenResponse second = objectMapper.readValue(refreshed, TokenResponse.class);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // 이미 쓴 토큰은 다시 통하지 않는다.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(first.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("다른 앱에서 발급된 카카오 토큰은 거부한다")
    void 다른_앱_토큰_거부() throws Exception {
        willThrow(new ApiException(ErrorCode.UNAUTHORIZED, "카카오 로그인에 실패했습니다."))
                .given(kakaoClient).resolveKakaoId(anyString());

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest("남의-앱-토큰"))))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("토큰 없이 보호된 API를 부르면 401과 공통 에러 형태로 응답한다")
    void 무인증_접근_차단() throws Exception {
        mockMvc.perform(delete("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.retryable").value(false));
    }

    @Test
    @DisplayName("탈퇴하면 사용자와 토큰이 함께 사라진다")
    void 탈퇴() throws Exception {
        TokenResponse tokens = objectMapper.readValue(
                login("kakao-token").getResponse().getContentAsString(), TokenResponse.class);

        mockMvc.perform(delete("/api/me")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
        assertThat(refreshTokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("빈 카카오 토큰은 400으로 막는다")
    void 요청_검증() throws Exception {
        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("진료 전 알림 설정은 기본이 켜짐이고 꺼도 계정에 남는다")
    void 알림_설정() throws Exception {
        String token = "Bearer " + objectMapper.readValue(
                login("kakao-token").getResponse().getContentAsString(),
                TokenResponse.class).accessToken();

        // 진료를 놓치지 않게 하는 것이 이 앱의 목적이라 기본은 켜짐이다.
        mockMvc.perform(get("/api/me/settings").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitReminderEnabled").value(true));

        mockMvc.perform(patch("/api/me/settings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitReminderEnabled").value(false));

        // 기기가 아니라 계정에 붙는다. 다시 읽어도 꺼져 있어야 한다.
        mockMvc.perform(get("/api/me/settings").header("Authorization", token))
                .andExpect(jsonPath("$.visitReminderEnabled").value(false));
    }

    @Test
    @DisplayName("설정 API 는 토큰이 필요하다")
    void 알림_설정_인증() throws Exception {
        mockMvc.perform(get("/api/me/settings"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.MvcResult login(String kakaoToken) throws Exception {
        return mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest(kakaoToken))))
                .andExpect(status().isOk())
                .andReturn();
    }
}
