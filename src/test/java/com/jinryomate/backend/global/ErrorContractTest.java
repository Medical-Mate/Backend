package com.jinryomate.backend.global;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import org.hamcrest.Matchers;
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
 * 잘못된 요청은 <b>클라이언트 잘못</b>으로 응답한다.
 *
 * <p>예전에는 스프링 MVC 예외가 전부 {@code @ExceptionHandler(Exception.class)} 로 떨어져
 * 500 이 나갔다. 앱이 필드 하나를 잘못 보내도 "서버 오류"로 보이니 원인을 앱에서 찾지 않고
 * 백엔드에 신고하게 된다. 운영에서 진짜 장애도 이 소음에 묻힌다.
 *
 * <p>운영 배포 후 실제로 확인해서 찾은 문제다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ErrorContractTest {

    private static final long KAKAO_ID = 4455667788L;

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
    @DisplayName("깨진 JSON은 400이다")
    void 깨진_본문() throws Exception {
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("파싱 실패 응답에 요청 본문이 새지 않는다")
    void 본문_비노출() throws Exception {
        // 이 API 의 본문은 증상·복용약이다. 파싱 오류 메시지에 섞여 나가면 안 된다.
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"어깨가 아프고 두통이 있어요\", 깨짐"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(Matchers.not(Matchers.containsString("두통"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("어깨"))));
    }

    @Test
    @DisplayName("숫자 자리에 문자가 오면 400이다")
    void 타입_불일치() throws Exception {
        mockMvc.perform(get("/api/cards/abc").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405다")
    void 메서드_불일치() throws Exception {
        mockMvc.perform(delete("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("읽을 수 없는 Content-Type은 415다")
    void 미디어타입_불일치() throws Exception {
        // 앱이 헤더를 잘못 붙였을 때 415 를 받으면 바로 자기 요청을 고친다.
        // 500 이 나가면 백엔드 장애로 보여 문의가 온다.
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("이름"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("만들 수 없는 Accept는 406이다")
    void 응답형식_불일치() throws Exception {
        mockMvc.perform(get("/api/health").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.error.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    @DisplayName("미디어 타입 오류에도 응답 형태가 같다")
    void 미디어타입_응답형태() throws Exception {
        // 앱이 오류 분기를 하나만 두면 되도록 형태를 통일한 것이 이 프로젝트의 결정이다.
        // 스프링 기본 ProblemDetail 이 섞이면 그 결정이 깨진다.
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content("아무거나"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.retryable").value(false))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    @DisplayName("없는 경로는 404다")
    void 없는_경로() throws Exception {
        mockMvc.perform(get("/api/no-such-path").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("모든 오류가 같은 형태를 지킨다")
    void 공통_형태() throws Exception {
        mockMvc.perform(get("/api/cards/abc").header("Authorization", token))
                .andExpect(jsonPath("$.error.code").exists())
                .andExpect(jsonPath("$.error.message").exists())
                .andExpect(jsonPath("$.error.retryable").value(false))
                // 앱이 로그를 요청할 때 짚을 값이다.
                .andExpect(jsonPath("$.meta.requestId").value(Matchers.startsWith("req_")));
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
