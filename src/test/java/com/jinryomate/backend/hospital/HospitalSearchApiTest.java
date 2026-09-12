package com.jinryomate.backend.hospital;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
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
 * 병원 찾기 (화면 1m-B).
 *
 * <p>서비스 키가 없으면 스텁이 돈다. 테스트에서는 키가 없는 것이 정상이라 스텁 경로를 본다 —
 * 실제 심평원을 부르면 테스트가 남의 서버와 트래픽 한도에 묶인다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalSearchApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(1122334455L);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("이름 일부로 병원을 찾는다")
    void 검색() throws Exception {
        mockMvc.perform(get("/api/hospitals?q=서울").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitals[0].name").exists())
                .andExpect(jsonPath("$.totalCount").isNumber());
    }

    @Test
    @DisplayName("주소가 함께 온다")
    void 주소() throws Exception {
        // 주소가 없으면 같은 이름의 다른 지점을 구별할 수 없다. "○○의원"은 검색하면
        // 여러 곳이 같은 줄로 보인다.
        mockMvc.perform(get("/api/hospitals?q=서울대학교병원").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitals[0].address").isNotEmpty())
                // 홈페이지는 화면이 쓰지 않는다. 처음엔 골라야 할 것을 잘못 골랐다.
                .andExpect(jsonPath("$.hospitals[0].url").doesNotExist());
    }

    @Test
    @DisplayName("주소가 없는 병원은 address가 null이다")
    void 주소_없음() throws Exception {
        mockMvc.perform(get("/api/hospitals?q=행복한의원").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hospitals[0].name").value("행복한의원"))
                .andExpect(jsonPath("$.hospitals[0].address").doesNotExist());
    }

    @Test
    @DisplayName("검색어가 비면 400")
    void 빈_검색어() throws Exception {
        mockMvc.perform(get("/api/hospitals?q=  ").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("한 번에 50개를 넘게 요청하면 400")
    void 크기_상한() throws Exception {
        mockMvc.perform(get("/api/hospitals?q=서울&size=100").header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰이 없으면 401")
    void 무인증() throws Exception {
        mockMvc.perform(get("/api/hospitals?q=서울"))
                .andExpect(status().isUnauthorized());
    }

    private TokenResponse login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }
}
