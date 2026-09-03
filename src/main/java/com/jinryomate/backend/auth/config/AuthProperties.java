package com.jinryomate.backend.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 발급 설정.
 *
 * <p>시크릿은 환경변수로만 주입한다. 공모전 제출물이라 코드가 공개될 수 있다.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record AuthProperties(
        String secret,
        Duration accessTtl,
        Duration refreshTtl
) {}
