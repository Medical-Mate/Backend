package com.jinryomate.backend.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서비스 호출 설정.
 *
 * <p>타임아웃을 코드가 아니라 설정으로 둔 이유: AI 파이프라인의 실제 응답 시간이
 * 아직 측정되지 않았다. 실측이 나오면 환경변수만 바꿔 조인다.
 *
 * <p>{@code hmacSecret} 이 비어 있으면 문답이 스텁으로 돈다
 * ({@link AiClientConfig}). 빈 값으로 서명하면 AI 가 요청을 전부 거부하므로,
 * 붙는 시늉을 하다 401 로 터지는 것보다 스텁이 낫다.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String baseUrl,
        String hmacSecret,
        Timeout timeout
) {
    public record Timeout(
            Duration turn,
            Duration card
    ) {}
}
