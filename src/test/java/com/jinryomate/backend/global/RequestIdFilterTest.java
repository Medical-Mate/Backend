package com.jinryomate.backend.global;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.global.web.RequestIdFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * requestId 는 <b>모든</b> 응답에 붙어야 한다.
 *
 * <p>예전에는 {@code RequestIdFilter} 가 기본 순서로 등록돼 스프링 시큐리티 체인보다
 * 뒤에 돌았다. 그래서 인증에서 잘린 401 응답에만 requestId 가 없었다 —
 * 정작 추적이 가장 필요한 응답이다. "로그인이 자꾸 풀린다"는 문의가 와도
 * 로그에서 그 요청을 찾을 수가 없었다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("인증에서 잘려도 requestId가 붙는다")
    void 인증_실패에도_붙는다() throws Exception {
        mockMvc.perform(get("/api/me/health-profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, Matchers.startsWith("req_")))
                // 헤더만 있고 본문이 비면 앱이 로그를 요청할 때 쓸 값이 없다.
                .andExpect(jsonPath("$.meta.requestId").value(Matchers.startsWith("req_")))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("인증이 필요 없는 경로에도 붙는다")
    void 공개_경로() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, Matchers.startsWith("req_")));
    }

    @Test
    @DisplayName("없는 경로의 404에도 붙는다")
    void 없는_경로() throws Exception {
        mockMvc.perform(get("/api/이런-건-없다"))
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    @DisplayName("클라이언트가 보낸 requestId를 이어받는다")
    void 이어받기() throws Exception {
        // 앱이 이미 id를 붙여 보냈으면 그걸 쓴다. 앱 로그와 서버 로그가 이어져야 한다.
        mockMvc.perform(get("/api/health").header(RequestIdFilter.HEADER, "req_from_app"))
                .andExpect(header().string(RequestIdFilter.HEADER, "req_from_app"));
    }

    @Test
    @DisplayName("이어받기는 인증 실패 경로에서도 동작한다")
    void 이어받기_인증_실패() throws Exception {
        mockMvc.perform(get("/api/me/health-profile").header(RequestIdFilter.HEADER, "req_from_app"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "req_from_app"))
                .andExpect(jsonPath("$.meta.requestId").value("req_from_app"));
    }
}
