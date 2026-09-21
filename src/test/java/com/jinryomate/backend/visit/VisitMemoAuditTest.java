package com.jinryomate.backend.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoRequest;
import com.jinryomate.backend.visit.entity.VisitMemoAudit;
import com.jinryomate.backend.visit.repository.VisitMemoAuditRepository;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.util.List;
import java.util.Map;
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
 * 진료 후 메모 분류의 감사 기록.
 *
 * <p>AI 회귀 eval 재료다(Medical-Mate/AI#113). <b>심사가 끝나면 테이블째 지우므로</b>
 * 이 테스트도 그때 함께 없어진다.
 *
 * <p>고정하려는 것은 셋이다 — 분류할 때마다 쌓이는가, 저장을 안 눌러도 남는가,
 * 한 메모를 여러 번 불렀을 때 {@code source} 로 구별되는가.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VisitMemoAuditTest {

    private static final long KAKAO_ID = 4471120386L;
    private static final String MEMO = "역류성 식도염이래요. 다음주에 위내시경 받기로 했어요.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired VisitMemoAuditRepository memoAuditRepository;
    @Autowired VisitRecordRepository visitRecordRepository;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("메모를 정리하면 AI 가 무엇을 보고 무엇을 뱉었는지 남는다")
    void 분류하면_쌓인다() throws Exception {
        classify(new ClassifyMemoRequest(MEMO, null, null, null, null, null, null));

        List<VisitMemoAudit> saved = memoAuditRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getAudit()).as("통째로 문자열이다").isNotBlank();
        assertThat(saved.getFirst().getSource()).isEqualTo("server");
    }

    @Test
    @DisplayName("기록을 저장하지 않고 나가도 감사 기록은 남는다")
    void 저장_안_해도_남는다() throws Exception {
        classify(new ClassifyMemoRequest(MEMO, null, null, null, null, null, null));

        // 저장 범위가 넓어지는 지점이다. 여기가 0 인데 감사 기록이 1 이라는 것이,
        // 마이그레이션 주석에 적어 둔 "정리만 해 보고 나간 메모도 남는다" 그 상태다.
        assertThat(visitRecordRepository.count()).isZero();
        assertThat(memoAuditRepository.count()).isOne();
    }

    @Test
    @DisplayName("한 메모를 여러 번 정리하면 전부 남고 source 로 갈린다")
    void 여러_번_불러도_전부_남는다() throws Exception {
        // 앱이 실제로 거치는 순서다. ① 문장만 나눈 뒤 ② 환자가 고친 라벨을 되보낸다.
        classify(new ClassifyMemoRequest(MEMO, null, null, null, false, null, null));
        classify(new ClassifyMemoRequest(MEMO, null, null,
                Map.of("0", "findings", "1", "tests"), null, null, "stub-split-v1"));

        // client 가 곧 환자가 고친 정답 라벨이다. 저장 시점에 server 만 남기면
        // eval 에 제일 값어치 있는 쪽이 사라진다 — 그래서 전부 남긴다.
        assertThat(memoAuditRepository.findAll())
                .extracting(VisitMemoAudit::getSource)
                .containsExactlyInAnyOrder("none", "client");
    }

    // ---------- helpers ----------

    private void classify(ClassifyMemoRequest request) throws Exception {
        mockMvc.perform(post("/api/visits/classify")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
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
