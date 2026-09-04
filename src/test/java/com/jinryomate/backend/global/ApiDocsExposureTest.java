package com.jinryomate.backend.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 문서가 <b>모든 프로필에서</b> 열리는지 확인한다.
 *
 * <p>예전에는 반대였다 — {@code local}·{@code dev} 에서만 열고 운영에서는 막았다.
 * 앱·AI 담당자가 배포된 서버를 보고 붙어야 하고 발표에서도 API 를 보여줘야 해서
 * 열기로 했다.
 *
 * <p>문서가 열려도 <b>엔드포인트는 여전히 JWT 를 요구한다.</b> 그 경계가 무너지지
 * 않는지도 여기서 함께 확인한다 — 문서를 열었다고 데이터까지 열리면 안 된다.
 */
class ApiDocsExposureTest {

    @Nested
    @Import(TestcontainersConfig.class)
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @DisplayName("개발 프로필이 아닐 때(운영 기준)")
    class 운영_기준 {

        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("OpenAPI 스펙이 무인증으로 열린다")
        void 스펙_공개() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("진료메이트 API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
        }

        @Test
        @DisplayName("Swagger UI가 무인증으로 열린다")
        void UI_공개() throws Exception {
            mockMvc.perform(get("/swagger-ui.html"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("문서가 열려도 API 자체는 여전히 인증을 요구한다")
        void 문서를_열어도_데이터는_닫혀_있다() throws Exception {
            // 문서 공개와 데이터 공개는 다른 이야기다. 이 경계가 무너지면
            // 문서를 연 판단 자체가 잘못된 것이 된다.
            mockMvc.perform(get("/api/me/health-profile"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/cards/1"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/me/share-links"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("actuator의 민감한 엔드포인트는 열리지 않는다")
        void actuator_차단() throws Exception {
            // 환경변수에 JWT 시크릿과 DB 비밀번호가 들어 있다.
            mockMvc.perform(get("/actuator/env"))
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
        @DisplayName("스펙에 앱이 먼저 붙는 엔드포인트가 담겨 있다")
        void 스펙_내용() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("진료메이트 API"))
                    // 앱이 가장 먼저 붙는 두 엔드포인트가 문서에 있어야 한다.
                    .andExpect(jsonPath("$.paths['/api/auth/kakao'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/me/health-profile'].put").exists());
        }
    }
}
