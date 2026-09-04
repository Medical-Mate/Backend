package com.jinryomate.backend.profile;

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
import com.jinryomate.backend.auth.client.KakaoProfile;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
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
 * 카카오에서 값을 받아 채워진 프로필 위에 온보딩을 저장한다.
 *
 * <p>기존 프로필 테스트는 <b>카카오 값이 하나도 없는</b> 상태에서만 온보딩을 저장한다.
 * 카카오 동의를 마친 실제 사용자는 이름·성별·출생연도가 이미 채워진 채로 온보딩에 들어오는데,
 * 그 경로가 검증된 적이 없었다.
 *
 * <p>운영 배포 후 실제 카카오 계정으로 로그인해 온보딩을 저장했더니 500 이 났다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OnboardingAfterKakaoPrefillTest {

    private static final long KAKAO_ID = 7788990011L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        // 실제 동의항목 응답과 같은 모양. 이름·성별·출생연도·생일이 모두 내려온다.
        given(kakaoClient.fetchProfile(anyString())).willReturn(new KakaoProfile(
                KAKAO_ID,
                new KakaoProfile.KakaoAccount("조현우", "male", "2001", "0106", "SOLAR")));
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("카카오 값이 프로필에 미리 채워진다")
    void 카카오_선반영() throws Exception {
        mockMvc.perform(get("/api/me/health-profile").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("조현우"))
                .andExpect(jsonPath("$.birthYear").value(2001))
                .andExpect(jsonPath("$.birthMonthDay").value("01-06"))
                .andExpect(jsonPath("$.sex").value("MALE"))
                .andExpect(jsonPath("$.sources.name").value("KAKAO"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
    }

    @Test
    @DisplayName("미리 채워진 프로필 위에 온보딩을 저장할 수 있다")
    void 선반영_후_온보딩() throws Exception {
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "조현우", 2001, "01-06", Sex.MALE,
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new TextFieldRequest(FieldStatus.UNKNOWN, null)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                // 온보딩에서 들어온 값은 카카오가 아니라 본인 입력으로 표시돼야 한다.
                .andExpect(jsonPath("$.sources.name").value("SELF_INPUT"));
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
}
