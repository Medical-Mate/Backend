package com.jinryomate.backend.ai.client;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * AI 서비스 호출에 붙이는 HMAC-SHA256 서명.
 *
 * <p>서명 대상은 이렇게 만든다.
 *
 * <pre>
 * message = "{METHOD}.{path}.{timestamp}.{request_id}." + body_bytes
 * </pre>
 *
 * <p><b>본문은 실제로 보낼 바이트 그대로여야 한다.</b> 객체를 넘겨받아 여기서 직렬화하면
 * 호출부가 보내는 바이트와 달라질 수 있다 — 공백이나 키 순서가 조금만 달라도 서명이
 * 틀어진다. 그래서 이 클래스는 {@code byte[]} 만 받는다.
 *
 * <p><b>{@code path} 는 쿼리스트링을 뺀다.</b> 앞의 {@code /} 는 포함한다. 프록시를 지난
 * 뒤의 경로여야 하는데, 우리는 Caddy 를 거치지 않고 Docker 내부망으로 AI 에 직접 붙으므로
 * 보낸 경로가 그대로 상대에게 간다.
 *
 * <p>규격이 맞는지는 AI 저장소가 준 벡터 10개로 검증한다
 * ({@code src/test/resources/ai/hmac-vectors.json}). 규격이 바뀌면 그 파일이 먼저 깨진다.
 */
public class AiSigner {

    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    public AiSigner(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("HMAC 시크릿이 비어 있습니다. 빈 값으로 서명하면 AI 가 전부 거부합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 서명값을 만든다.
     *
     * @param method    대문자 HTTP 메서드
     * @param path      쿼리 제외, 앞 {@code /} 포함
     * @param timestamp epoch 초
     * @param requestId 없으면 {@code null} — 그 자리는 빈 문자열이 된다
     * @param body      보낼 바이트 그대로. 본문이 없으면 빈 배열
     */
    public String sign(String method, String path, Instant timestamp, String requestId, byte[] body) {
        String prefix = method + "." + path + "." + timestamp.getEpochSecond() + "."
                + (requestId == null ? "" : requestId) + ".";

        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[prefixBytes.length + body.length];
        System.arraycopy(prefixBytes, 0, message, 0, prefixBytes.length);
        System.arraycopy(body, 0, message, prefixBytes.length, body.length);

        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // HmacSHA256 은 표준 JDK 알고리즘이라 여기 오면 런타임이 망가진 것이다.
            throw new IllegalStateException("HMAC 서명을 만들 수 없습니다.", e);
        }
    }
}
