package com.jinryomate.backend.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 웹 데모 경로 (한시).
 *
 * <p><b>AI 통로는 여기서 끝까지 못 본다.</b> 테스트에는 HMAC 시크릿이 없어 프록시 빈이
 * 없고, 있어도 실제 AI 를 불러야 한다. 대신 <b>인증 없이 열려 있는지</b>와
 * <b>문진 밖은 안 열렸는지</b>를 고정한다 — 그게 이 경로에서 틀리면 제일 비싼 것이다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoApiTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("토큰 없이 부위 마스터를 받는다")
    void 부위_마스터() throws Exception {
        // 브라우저에는 계정이 없다. 여기가 막히면 데모가 첫 화면에서 멈춘다.
        mockMvc.perform(get("/api/demo/body-map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ontology_snapshot").exists())
                .andExpect(jsonPath("$.anchors").isArray());
    }

    @Test
    @DisplayName("문답 경로도 토큰 없이 열린다")
    void 문답_경로가_열려_있다() throws Exception {
        // 시크릿이 없는 환경이라 503 이다. 중요한 것은 401 이 아니라는 것 —
        // 401 이면 SecurityConfig 에서 경로가 빠진 것이다.
        mockMvc.perform(post("/api/demo/previsit/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/api/demo/previsit/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("문진 밖은 데모로 열리지 않는다")
    void 문진_밖은_안_열린다() throws Exception {
        // 카드·기록·일정까지 무인증으로 열면 데모가 아니라 계정 없는 서비스가 된다.
        // 누가 편의로 /api/demo 아래에 경로를 더하면 여기서 걸린다.
        mockMvc.perform(get("/api/demo/cards")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/demo/visits")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/demo/appointments")).andExpect(status().isNotFound());

        // 진료 후 메모는 Bedrock 을 부른다. 데모에 열려 있으면 크레딧이 샌다.
        mockMvc.perform(post("/api/demo/postvisit/memo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("계정이 필요한 경로는 여전히 401 이다")
    void 본래_경로는_그대로다() throws Exception {
        // 데모 경로를 열면서 다른 경로까지 열리면 안 된다.
        mockMvc.perform(get("/api/me/cards")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/me/appointments/upcoming")).andExpect(status().isUnauthorized());
    }
}
