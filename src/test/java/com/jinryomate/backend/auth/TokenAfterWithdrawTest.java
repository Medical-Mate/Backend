package com.jinryomate.backend.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.RefreshRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
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
 * 탈퇴하면 <b>이미 발급된 액세스 토큰도 즉시 통하지 않는다.</b>
 *
 * <p>JWT 는 무상태라 한 번 나가면 만료까지 회수할 수 없다. 탈퇴하면 refresh 는 지워져
 * 재발급이 막히지만, 액세스 토큰은 수명(1시간)만큼 그대로 통했다. 탈퇴는 "지금 끊어달라"는
 * 요청인데 한 시간 뒤에 끊기면 절반만 들어준 것이다.
 *
 * <p>특히 {@code GET /api/me/health-profile} 은 {@code userId} 로만 조회해서
 * 사용자 존재를 보지 않는다. 탈퇴한 사람에게 <b>빈 프로필 200</b> 이 나갔다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TokenAfterWithdrawTest {

    private static final long KAKAO_ID = 1029384756L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private TokenResponse tokens;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        tokens = login();
    }

    @Test
    @DisplayName("탈퇴 전에는 액세스 토큰이 통한다")
    void 탈퇴_전() throws Exception {
        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴하면 같은 액세스 토큰이 즉시 막힌다")
    void 탈퇴_후_액세스_토큰() throws Exception {
        withdraw();

        // 토큰 자체는 아직 만료되지 않았다. 서명도 멀쩡하다.
        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("탈퇴하면 refresh 토큰도 통하지 않는다")
    void 탈퇴_후_refresh() throws Exception {
        withdraw();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RefreshRequest(tokens.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴한 사용자가 다시 로그인하면 새 회원이 된다")
    void 재가입() throws Exception {
        withdraw();

        // 카카오 연결은 그대로라 재동의 없이 토큰이 나온다. 우리 입장에서는 처음 보는
        // 회원번호이므로 새로 가입시킨다 — 예전 카드·기록은 딸려 오지 않는다.
        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingRequired").value(true));
    }

    // ---------- helpers ----------

    private String bearer() {
        return "Bearer " + tokens.accessToken();
    }

    private void withdraw() throws Exception {
        mockMvc.perform(delete("/api/me").header("Authorization", bearer()))
                .andExpect(status().isNoContent());
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
