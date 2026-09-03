package com.jinryomate.backend.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서비스 호출 설정.
 *
 * <p>타임아웃을 코드가 아니라 설정으로 둔 이유: AI 파이프라인의 실제 응답 시간이
 * 아직 측정되지 않았다. 실측이 나오면 환경변수만 바꿔 조인다.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String baseUrl,
        String internalToken,
        Timeout timeout
) {
    public record Timeout(
            Duration turn,
            Duration card
    ) {}
}
