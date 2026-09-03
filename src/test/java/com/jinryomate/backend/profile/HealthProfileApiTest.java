package com.jinryomate.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.client.KakaoProfile;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.time.Year;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온보딩 프로필 API 통합 테스트.
 *
 * <p>카카오 API는 목으로 대체한다. 동의 거부(값 없음)와 동의 허용(값 있음)을 모두 확인한다 —
 * 값이 없는 경우가 예외가 아니라 정상 경로다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HealthProfileApiTest {

    private static final long KAKAO_ID = 3847562910L;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
    }

    @Test
    @DisplayName("카카오 동의를 거부하면 프로필이 비어 있고 온보딩이 필요하다")
    void 동의_거부시_빈_프로필() throws Exception {
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);

        TokenResponse tokens = login();
        assertThat(tokens.onboardingRequired()).isTrue();

        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.canStartIntake").value(false));
    }

    @Test
    @DisplayName("카카오에서 받은 값이 프로필에 미리 채워지고 출처가 KAKAO로 남는다")
    void 카카오_값_선반영() throws Exception {
        givenKakaoProfile("김서연", "female", "1994", "0303", "SOLAR");

        TokenResponse tokens = login();

        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김서연"))
                .andExpect(jsonPath("$.sex").value("FEMALE"))
                .andExpect(jsonPath("$.birthYear").value(1994))
                .andExpect(jsonPath("$.birthMonthDay").value("03-03"))
                // 생일이 있으면 올해에서 빼는 근사값이 아니라 만 나이를 정확히 계산한다.
                .andExpect(jsonPath("$.age").value(exactAge(1994, "03-03")))
                .andExpect(jsonPath("$.sources.name").value("KAKAO"))
                // 카카오 값이 채워졌어도 온보딩을 마친 것은 아니다.
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                // 나이·성별이 있으므로 문답은 시작할 수 있다.
                .andExpect(jsonPath("$.canStartIntake").value(true));
    }

    @Test
    @DisplayName("연령대만 오고 출생연도가 없으면 나이를 만들지 못한다")
    void 출생연도_없으면_나이_없음() throws Exception {
        givenKakaoProfile("김서연", "female", null, null, null);

        TokenResponse tokens = login();

        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.birthYear").doesNotExist())
                .andExpect(jsonPath("$.age").doesNotExist())
                .andExpect(jsonPath("$.canStartIntake").value(false));
    }

    @Test
    @DisplayName("온보딩을 저장하면 세 값 상태가 그대로 보존된다")
    void 온보딩_저장() throws Exception {
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        TokenResponse tokens = login();

        HealthProfileRequest request = new HealthProfileRequest(
                "김서연", 1994, "03-03", Sex.FEMALE,
                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜", "비타민D")),
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new TextFieldRequest(FieldStatus.UNKNOWN, null));

        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medications.status").value("KNOWN"))
                .andExpect(jsonPath("$.medications.items.length()").value(2))
                // "없어요"와 "잘 모르겠어요"는 서로 다른 값으로 남아야 한다.
                .andExpect(jsonPath("$.conditions.status").value("NONE"))
                .andExpect(jsonPath("$.allergies.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.allergies.text").doesNotExist())
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.sources.name").value("SELF_INPUT"));
    }

    @Test
    @DisplayName("사용자가 고친 값은 다시 로그인해도 카카오 값으로 덮이지 않는다")
    void 직접_입력값_보존() throws Exception {
        givenKakaoProfile("김서연", "female", "1994", "0303", "SOLAR");
        TokenResponse tokens = login();

        HealthProfileRequest request = new HealthProfileRequest(
                "김민지", 1990, "07-15", Sex.FEMALE,
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new TextFieldRequest(FieldStatus.NONE, null));

        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 카카오는 여전히 원래 값을 주지만, 사용자가 고친 값이 이겨야 한다.
        TokenResponse second = login();
        assertThat(second.onboardingRequired()).isFalse();

        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(second)))
                .andExpect(jsonPath("$.name").value("김민지"))
                .andExpect(jsonPath("$.birthYear").value(1990))
                .andExpect(jsonPath("$.sources.name").value("SELF_INPUT"));
    }

    @Test
    @DisplayName("출생연도가 비면 400으로 막는다")
    void 요청_검증() throws Exception {
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        TokenResponse tokens = login();

        HealthProfileRequest invalid = new HealthProfileRequest(
                "김서연", null, null, Sex.FEMALE,
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new ListFieldRequest(FieldStatus.NONE, List.of()),
                new TextFieldRequest(FieldStatus.NONE, null));

        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", bearer(tokens))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("토큰 없이 프로필을 조회하면 401")
    void 무인증_차단() throws Exception {
        mockMvc.perform(get("/api/me/health-profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("생일만 동의하고 출생연도를 동의하지 않으면 나이를 만들지 못한다")
    void 생일만_있으면_나이_없음() throws Exception {
        // 생일과 출생연도는 카카오에서 별개 동의항목이다. 생일만 켜면 MMDD 만 온다.
        givenKakaoProfile("김서연", "female", null, "0303", "SOLAR");

        TokenResponse tokens = login();

        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.birthMonthDay").value("03-03"))
                .andExpect(jsonPath("$.birthYear").doesNotExist())
                .andExpect(jsonPath("$.age").doesNotExist())
                .andExpect(jsonPath("$.canStartIntake").value(false));
    }

    @Test
    @DisplayName("음력 생일은 나이 계산에 쓰지 않는다")
    void 음력_생일은_무시한다() throws Exception {
        givenKakaoProfile("김서연", "female", "1994", "0303", "LUNAR");

        TokenResponse tokens = login();

        // 양력 변환 없이 그대로 쓰면 오히려 틀린 나이가 나오므로 출생연도만으로 근사한다.
        mockMvc.perform(get("/api/me/health-profile").header("Authorization", bearer(tokens)))
                .andExpect(jsonPath("$.birthMonthDay").doesNotExist())
                .andExpect(jsonPath("$.age").value(Year.now().getValue() - 1994));
    }

    // ---------- helpers ----------

    private void givenKakaoProfile(String name, String gender, String birthYear,
                                   String birthday, String birthdayType) {
        given(kakaoClient.fetchProfile(anyString())).willReturn(
                new KakaoProfile(KAKAO_ID,
                        new KakaoProfile.KakaoAccount(name, gender, birthYear, birthday, birthdayType)));
    }

    private static int exactAge(int birthYear, String monthDay) {
        LocalDate birth = MonthDay.parse("--" + monthDay).atYear(birthYear);
        return Period.between(birth, LocalDate.now()).getYears();
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

    private String bearer(TokenResponse tokens) {
        return "Bearer " + tokens.accessToken();
    }

}
