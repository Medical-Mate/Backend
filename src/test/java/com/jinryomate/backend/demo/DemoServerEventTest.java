package com.jinryomate.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.demo.entity.DemoEvent;
import com.jinryomate.backend.demo.repository.DemoEventRepository;
import com.jinryomate.backend.demo.service.DemoEventRecorder;
import com.jinryomate.backend.demo.service.DemoRateLimiter;
import com.jinryomate.backend.demo.web.DemoBodySizeFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
 * 서버가 자기 값을 남기는지.
 *
 * <p><b>브라우저는 자기가 막힌 것만 압니다.</b> 전체 200회/분에 걸려 떨어진 사람이 몇인지는
 * 서버만 알고, 그래서 이 줄들이 없으면 심사 중에 사람들이 막히고 있어도 모릅니다.
 *
 * <p>웹은 PostHog 로 화면 흐름을 따로 보고 있어, 여기서 겹치는 것은 재지 않습니다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoServerEventTest {

    @Autowired MockMvc mockMvc;
    @Autowired DemoEventRepository repository;
    @Autowired DemoRateLimiter rateLimiter;
    @Autowired DemoEventRecorder recorder;

    @BeforeEach
    @AfterEach
    void 비우기() {
        rateLimiter.reset();
        recorder.reset();
        repository.deleteAll();
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private List<DemoEvent> rowsOf(String event) {
        return repository.findAll().stream().filter(r -> r.getEvent().equals(event)).toList();
    }

    @Test
    @DisplayName("빈도 제한에 막히면 서버가 남긴다")
    void 빈도_제한을_남긴다() throws Exception {
        String ip = "203.0.113.31";
        for (int i = 0; i < 31; i++) {
            mockMvc.perform(get("/api/demo/body-map").with(from(ip)));
        }

        List<DemoEvent> blocked = rowsOf("guard.rate_limited");
        assertThat(blocked).as("31번째가 막히면서 한 줄 남아야 한다").isNotEmpty();

        DemoEvent row = blocked.get(0);
        assertThat(row.getSurface()).isEqualTo("server");
        assertThat(row.getProps()).containsEntry("path", "body-map");
        // 요청 id 로 백엔드 로그와 이어진다.
        assertThat(row.getSessionId()).isNotBlank();
    }

    @Test
    @DisplayName("본문 크기에 막히면 서버가 남긴다")
    void 크기_초과를_남긴다() throws Exception {
        byte[] tooBig = new byte[(int) DemoBodySizeFilter.MAX_BYTES + 1];
        Arrays.fill(tooBig, (byte) 'a');

        mockMvc.perform(post("/api/demo/previsit/turns")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tooBig)
                .with(from("203.0.113.32")));

        List<DemoEvent> blocked = rowsOf("guard.payload_too_large");
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).getProps()).containsEntry("path", "previsit/turns");
    }

    /**
     * 카탈로그를 서버도 똑같이 통과한다.
     *
     * <p>서버가 쓰는 길이라고 느슨해지면 화이트리스트를 둔 의미가 없다. 여기가 뚫리면
     * 나중에 누가 "서버에서 넣는 거니까" 하고 발화를 담는다.
     */
    @Test
    @DisplayName("카탈로그 밖이면 서버가 넣어도 저장되지 않는다")
    void 서버도_카탈로그를_지킨다() {
        recorder.record("intake.turn_sent", Map.of("utterance", "배가 아파요"));
        recorder.record("made.up.event", Map.of());

        assertThat(repository.count()).isZero();
    }

    /**
     * 분당 상한.
     *
     * <p>{@code guard.rate_limited} 는 하필 두들겨 맞을 때 터지는 이벤트다. 막을 때마다
     * 한 줄씩 쓰면 <b>공격을 우리가 증폭한다</b> — 상대는 요청 한 번, 우리는 INSERT 한 번.
     */
    @Test
    @DisplayName("분당 상한을 넘으면 더 쓰지 않는다")
    void 기록에도_상한이_있다() {
        for (int i = 0; i < 200; i++) {
            recorder.record("severity.viewed");
        }

        assertThat(repository.count())
                .as("120 에서 멈춰야 한다")
                .isEqualTo(120);
    }
}
