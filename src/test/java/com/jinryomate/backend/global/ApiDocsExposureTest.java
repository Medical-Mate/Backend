package com.jinryomate.backend.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 문서가 개발 프로필에서만 열리는지 확인한다.
 *
 * <p>운영에 문서가 열린 채 나가는 사고를 막는 것이 이 테스트의 목적이다.
 * 진료 API의 전체 구조는 공격자에게 지도가 된다.
 */
class ApiDocsExposureTest {

    @Nested
    @Import(TestcontainersConfig.class)
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("개발 프로필이 아닐 때")
    class 운영_기준 {

        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("OpenAPI 스펙이 열리지 않는다")
        void 스펙_차단() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Swagger UI가 열리지 않는다")
        void UI_차단() throws Exception {
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @Import(TestcontainersConfig.class)
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "local"})
    @DisplayName("local 프로필일 때")
    class 개발_기준 {

        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("OpenAPI 스펙이 무인증으로 열리고 인증 스킴이 담겨 있다")
        void 스펙_공개() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("진료메이트 API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    // 앱이 가장 먼저 붙는 두 엔드포인트가 문서에 있어야 한다.
                    .andExpect(jsonPath("$.paths['/api/auth/kakao'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/me/health-profile'].put").exists());
        }
    }
}
