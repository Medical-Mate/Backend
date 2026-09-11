package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.client.AiSigner;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * AI 저장소가 준 서명 벡터로 우리 구현을 대조한다.
 *
 * <p>벡터는 {@code Medical-Mate/AI} 의 {@code docs/examples/hmac-vectors.json} 을 그대로
 * 복사한 것이다. <b>벡터마다 한 가지씩만 다르다</b> — 경로만, 메서드만, 쿼리만. 그래서
 * 틀렸을 때 어디서 갈렸는지가 실패한 벡터 이름으로 바로 나온다.
 *
 * <p>서명이 틀리면 AI 가 401 로 전부 거부하고, 그 실패는 문답 첫 화면에서 터진다.
 * 규격이 바뀌면 여기가 먼저 깨져야 한다.
 */
class AiSignerTest {

    private static final String VECTORS = "/ai/hmac-vectors.json";

    record Vector(String id, String note, String method, String path,
                  String timestamp, String requestId, String body, String signature) {
        @Override
        public String toString() {
            return id + " — " + note;
        }
    }

    static List<Vector> vectors() throws Exception {
        try (InputStream in = AiSignerTest.class.getResourceAsStream(VECTORS)) {
            assertThat(in).as("벡터 파일이 있어야 한다: %s", VECTORS).isNotNull();
            JsonNode root = new ObjectMapper().readTree(in);

            List<Vector> out = new ArrayList<>();
            for (JsonNode v : root.get("vectors")) {
                out.add(new Vector(
                        v.get("id").asText(),
                        v.path("note").asText(""),
                        v.get("method").asText(),
                        v.get("path").asText(),
                        v.get("timestamp").asText(),
                        v.path("request_id").isMissingNode() || v.path("request_id").isNull()
                                ? null : v.get("request_id").asText(),
                        v.get("body").asText(),
                        v.get("signature").asText()));
            }
            return out;
        }
    }

    static String secret() throws Exception {
        try (InputStream in = AiSignerTest.class.getResourceAsStream(VECTORS)) {
            return new ObjectMapper().readTree(in).get("secret").asText();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    @DisplayName("AI가 준 서명 벡터와 값이 같다")
    void 벡터_대조(Vector v) throws Exception {
        String actual = new AiSigner(secret()).sign(
                v.method(),
                v.path(),
                Instant.ofEpochSecond(Long.parseLong(v.timestamp())),
                v.requestId(),
                v.body().getBytes(StandardCharsets.UTF_8));

        assertThat(actual).isEqualTo(v.signature());
    }

    @Test
    @DisplayName("벡터가 10개 다 로드된다")
    void 벡터_개수() throws Exception {
        // 파일이 비거나 형식이 바뀌면 위 테스트가 0건으로 통과해버린다.
        assertThat(vectors()).hasSize(10);
    }

    @Test
    @DisplayName("본문이 같아도 경로가 다르면 서명이 다르다")
    void 경로가_서명에_들어간다() throws Exception {
        AiSigner signer = new AiSigner(secret());
        Instant now = Instant.ofEpochSecond(1789000001L);
        byte[] body = "{\"utterance\":\"무릎\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.sign("POST", "/v1/previsit/turns", now, "req", body))
                .isNotEqualTo(signer.sign("POST", "/v1/previsit/sessions", now, "req", body));
    }

    @Test
    @DisplayName("빈 시크릿은 거부한다")
    void 빈_시크릿() {
        // 빈 값으로 서명하면 AI 가 전부 401 로 거부한다. 기동 때 걸리는 편이 낫다.
        assertThatThrownBy(() -> new AiSigner(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiSigner(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
