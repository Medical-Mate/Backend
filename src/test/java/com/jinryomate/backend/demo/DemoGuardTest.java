package com.jinryomate.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.demo.service.DemoRateLimiter;
import com.jinryomate.backend.demo.web.DemoBodySizeFilter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 데모 경로를 지키는 둘 — 호출 빈도와 본문 크기.
 *
 * <p><b>왜 따로 두나</b> — {@link DemoApiTest} 는 "무엇이 열려 있나"를 고정한다. 여기는
 * "얼마나까지 허용하나"를 고정한다. 둘을 섞으면 한쪽을 손댈 때 다른 쪽 기대가 함께
 * 흔들린다.
 *
 * <p><b>이 테스트가 없던 동안</b> 빈도 제한은 코드만 있고 검증이 없었다. 인증이 없는
 * 경로의 유일한 접근 제어인데 "켜 놓고 안 걸려 있는" 상태를 확인할 방법이 없었다.
 *
 * <p><b>창을 앞뒤로 비운다.</b> 제한이 정적 상태라 안 비우면 이 테스트가 다음 테스트를
 * 429 로 떨어뜨린다 — 검증하려던 것이 다른 테스트를 깨는 셈이다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoGuardTest {

    @Autowired MockMvc mockMvc;
    @Autowired DemoRateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void 창_비우기() {
        rateLimiter.reset();
    }

    /** 같은 IP 에서 온 것처럼 보내려고. 제한이 IP 별이라 안 고정하면 갈라진다. */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private int bodyMap(String ip) throws Exception {
        return mockMvc.perform(get("/api/demo/body-map").with(from(ip)))
                .andReturn().getResponse().getStatus();
    }

    /**
     * 빈도 제한이 상한에서 정확히 건다.
     *
     * <p><b>"몇 번은 통과한다"로는 부족하다.</b> 상한을 넘겼을 때 걸리는지까지 봐야
     * 제한이 실제로 붙어 있다고 말할 수 있다. 부위 마스터는 AI 를 부르지 않아 비용
     * 없이 반복할 수 있다.
     */
    @Test
    @DisplayName("IP 당 30회까지 통과하고 31회째에 429")
    void 빈도_제한() throws Exception {
        String ip = "203.0.113.9";

        for (int i = 1; i <= 30; i++) {
            assertThat(bodyMap(ip)).as("%d번째는 통과해야 한다", i).isEqualTo(200);
        }

        assertThat(bodyMap(ip)).as("31번째는 막혀야 한다").isEqualTo(429);
    }

    /** 제한이 IP 별이다. 한 사람이 막혔다고 옆 사람까지 막히면 안 된다. */
    @Test
    @DisplayName("한 IP 가 막혀도 다른 IP 는 통과한다")
    void 제한은_IP_별() throws Exception {
        String blocked = "203.0.113.20";
        for (int i = 0; i < 31; i++) {
            bodyMap(blocked);
        }
        assertThat(bodyMap(blocked)).isEqualTo(429);

        assertThat(bodyMap("203.0.113.21")).as("다른 IP 는 영향받지 않는다").isEqualTo(200);
    }

    /** 걸렸을 때 앱이 분기할 수 있는 형태로 나가는지. */
    @Test
    @DisplayName("429 도 다른 오류와 같은 봉투로 나간다")
    void 빈도_제한_응답_모양() throws Exception {
        String ip = "203.0.113.10";
        for (int i = 0; i < 30; i++) {
            bodyMap(ip);
        }

        mockMvc.perform(get("/api/demo/body-map").with(from(ip)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_REQUESTS"));
    }

    /**
     * 본문 크기 상한.
     *
     * <p>인증이 없는 경로가 본문을 통째로 메모리에 올린다. 빈도 제한은 <b>횟수만</b>
     * 막으므로 크기는 따로 막아야 한다.
     */
    @Test
    @DisplayName("상한보다 큰 본문은 413 으로 끊는다")
    void 본문_크기_제한() throws Exception {
        mockMvc.perform(post("/api/demo/previsit/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooBig())
                        .with(from("203.0.113.11")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    /**
     * 상한 안쪽은 통과한다.
     *
     * <p><b>막는 것만 확인하면 절반이다.</b> 너무 좁게 잡아 정상 문답이 막히는 것도
     * 같은 종류의 사고다. 테스트에는 HMAC 시크릿이 없어 프록시 빈이 없으므로 413 이
     * 아니기만 하면 된다 — 크기에서 안 걸리고 다음 단계까지 갔다는 뜻이다.
     */
    @Test
    @DisplayName("상한 안쪽 본문은 크기에서 막히지 않는다")
    void 정상_크기는_통과() throws Exception {
        byte[] ok = "{\"state\":null,\"utterance\":\"배가 아파요\"}".getBytes(StandardCharsets.UTF_8);

        int status = mockMvc.perform(post("/api/demo/previsit/turns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ok)
                        .with(from("203.0.113.12")))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(413);
    }

    /** 데모 밖에는 안 건다. 앱 경로는 계정 뒤에 있고 본문 모양이 다르다. */
    @Test
    @DisplayName("앱 경로는 이 상한을 받지 않는다")
    void 앱_경로는_제외() throws Exception {
        // 인증이 먼저 걸려 401 이다. 413 이면 데모 밖까지 상한이 번진 것이다.
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooBig()))
                .andExpect(status().isUnauthorized());
    }

    private static byte[] tooBig() {
        byte[] body = new byte[(int) DemoBodySizeFilter.MAX_BYTES + 1];
        Arrays.fill(body, (byte) 'a');
        return body;
    }
}
