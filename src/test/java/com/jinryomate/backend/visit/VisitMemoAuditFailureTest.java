package com.jinryomate.backend.visit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.visit.repository.VisitMemoAuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 저장이 실패해도 환자의 메모 분류는 돌아야 한다.
 *
 * <p>감사 행 하나를 못 써서 환자가 방금 적은 메모가 500 으로 날아가면 안 된다.
 * {@code HttpAiMemoClient} 가 "모르는 값이 와도 막지 않는다" 로 잡아 둔 것과 같은
 * 저울이다 — 축 하나를 잃는 손해가 메모를 통째로 잃는 손해보다 작다.
 *
 * <p>클래스를 따로 둔 것은 {@code @MockitoBean} 이 클래스 전체에 걸려서, 같이 두면
 * {@link VisitMemoAuditTest} 의 저장 검증이 못 돈다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VisitMemoAuditFailureTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;
    @MockitoBean VisitMemoAuditRepository memoAuditRepository;

    @Test
    @DisplayName("감사 기록 저장이 실패해도 메모 분류는 200 으로 돌아온다")
    void 저장이_실패해도_분류는_돈다() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(9174036285L);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        willThrow(new DataIntegrityViolationException("일부러 낸 오류"))
                .given(memoAuditRepository).save(any());

        String token = "Bearer " + login().accessToken();

        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memo\":\"위염이래요. 다음주에 다시 오래요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentences").isArray());
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
