package com.jinryomate.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.demo.repository.DemoEventRepository;
import com.jinryomate.backend.demo.service.DemoRateLimiter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

/**
 * 이벤트 수집의 계약.
 *
 * <p><b>여기서 지키는 것은 "무엇이 저장되지 않는가" 입니다.</b> 이벤트 경로는 인증이 없고
 * 브라우저가 보낸 것을 받습니다 — 화이트리스트가 뚫리면 증상 텍스트가 그대로 DB 에 쌓이고,
 * 웹 데모가 내건 "증상 내용은 저장하지 않습니다" 가 거짓이 됩니다.
 *
 * <p>그래서 통과 경로보다 <b>막히는 경로</b>에 테스트가 더 많습니다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoEventTest {

    @Autowired MockMvc mockMvc;
    @Autowired DemoEventRepository repository;
    @Autowired DemoRateLimiter rateLimiter;

    @BeforeEach
    @AfterEach
    void 비우기() {
        rateLimiter.reset();
        repository.deleteAll();
    }

    /** 검사에 걸리지 않도록 지금 시각을 쓴다. 고정하면 ±24시간 창을 벗어난다. */
    private static String now() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String one(String props) {
        return """
                {"events":[{"event":"intake.turn_sent","occurredAt":"%s",
                "sessionId":"s_abcd1234efgh","seq":1,"surface":"web",
                "build":"web-test","props":%s}]}
                """.formatted(now(), props);
    }

    private int send(String body) throws Exception {
        return mockMvc.perform(post("/api/demo/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("규격에 맞는 묶음은 202 로 받고 저장한다")
    void 정상_배치() throws Exception {
        mockMvc.perform(post("/api/demo/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(one("""
                                {"turn_no":3,"input_mode":"voice","char_count":42}""")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.accepted").value(1));

        assertThat(repository.count()).isEqualTo(1);
    }

    /** 속성은 선택이다. 지연을 못 재는 브라우저가 있어 강제하면 이벤트가 통째로 막힌다. */
    @Test
    @DisplayName("속성이 없어도 통과한다")
    void 속성_없음() throws Exception {
        assertThat(send(one("{}"))).isEqualTo(202);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("카탈로그에 없는 이벤트는 400")
    void 모르는_이벤트() throws Exception {
        String body = one("{}").replace("intake.turn_sent", "intake.something_new");

        mockMvc.perform(post("/api/demo/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }

    /**
     * <b>이 테스트가 이 파일의 이유입니다.</b>
     *
     * <p>프론트 버그로든 누가 손으로 찔러서든, 허용하지 않은 키에 발화가 실려 오면
     * 저장되지 않아야 합니다. 여기가 뚫리면 나머지 규칙이 다 무의미합니다.
     */
    @Test
    @DisplayName("허용하지 않는 속성은 400 — 증상 텍스트가 실려와도 저장되지 않는다")
    void 허용하지_않는_속성() throws Exception {
        assertThat(send(one("""
                {"turn_no":3,"utterance":"배가 쥐어짜듯 아파요"}"""))).isEqualTo(400);

        assertThat(repository.count()).isZero();
    }

    @Test
    @DisplayName("속성 값이 규격 밖이면 400")
    void 규격_밖_값() throws Exception {
        // turn_no 는 1~50 이다.
        assertThat(send(one("""
                {"turn_no":9999}"""))).isEqualTo(400);

        // input_mode 는 닫힌 목록이다.
        assertThat(send(one("""
                {"input_mode":"telepathy"}"""))).isEqualTo(400);

        assertThat(repository.count()).isZero();
    }

    /**
     * 하나라도 걸리면 묶음 전체가 들어가지 않는다.
     *
     * <p>좋은 것만 골라 넣으면 프론트 버그가 계속 숨습니다 — 클라이언트 버그는 체계적이라
     * 모든 묶음에서 같은 이벤트가 떨어지고, 통째로 400 이 나야 그날 안에 눈에 띕니다.
     */
    @Test
    @DisplayName("하나가 규격 밖이면 묶음 전체가 저장되지 않는다")
    void 부분_저장_없음() throws Exception {
        String body = """
                {"events":[
                  {"event":"card.confirmed","occurredAt":"%s","sessionId":"s_abcd1234efgh",
                   "seq":1,"surface":"web","props":{}},
                  {"event":"card.axis_edited","occurredAt":"%s","sessionId":"s_abcd1234efgh",
                   "seq":2,"surface":"web","props":{"axis_key":"onset"}},
                  {"event":"memo.submitted","occurredAt":"%s","sessionId":"s_abcd1234efgh",
                   "seq":3,"surface":"web","props":{"memo":"연골이 닳았대요"}}
                ]}
                """.formatted(now(), now(), now());

        assertThat(send(body)).isEqualTo(400);
        assertThat(repository.count()).as("앞의 둘도 들어가면 안 된다").isZero();
    }

    /**
     * 오프셋이 없으면 UTC 로 조용히 해석되어 시간대 분석이 아홉 시간 틀어진다.
     *
     * <p><b>{@code one()} 을 고쳐 쓰지 않습니다.</b> 그 안에서 부른 {@code now()} 와 여기서
     * 부른 {@code now()} 는 초가 갈리면 다른 문자열이라, {@code replace} 가 아무것도 못 바꾸고
     * 멀쩡한 요청이 202 로 통과합니다 — 한 번은 그렇게 가짜로 통과했습니다.
     */
    @Test
    @DisplayName("오프셋 없는 시각은 400")
    void 오프셋_없는_시각() throws Exception {
        String body = """
                {"events":[{"event":"card.confirmed","occurredAt":"2026-09-21T14:03:22",
                "sessionId":"s_abcd1234efgh","seq":1,"surface":"web","props":{}}]}
                """;

        assertThat(send(body)).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    /** 배치 상한. 넘으면 한 요청이 너무 커지고 빈도 제한의 의미도 흐려진다. */
    @Test
    @DisplayName("한 번에 50개를 넘으면 400")
    void 배치_상한() throws Exception {
        String items = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> """
                        {"event":"card.confirmed","occurredAt":"%s",
                         "sessionId":"s_abcd1234efgh","seq":%d,"surface":"web","props":{}}"""
                        .formatted(now(), i))
                .collect(Collectors.joining(","));

        assertThat(send("{\"events\":[" + items + "]}")).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    /** 문답 세션 id 를 그대로 쓰지 못하게 형식을 좁혀 둔다. */
    @Test
    @DisplayName("세션 키 형식이 어긋나면 400")
    void 세션_키_형식() throws Exception {
        String body = one("{}").replace("s_abcd1234efgh", "짧음");

        assertThat(send(body)).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }
}
