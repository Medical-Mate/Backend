package com.jinryomate.backend.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 발급 설정.
 *
 * <p>시크릿은 환경변수로만 주입한다. 공모전 제출물이라 코드가 공개될 수 있다.
 *
 * <p><b>값이 없으면 기동하지 않는다.</b> 예전에는 {@code application.yml} 에
 * 개발용 기본값이 박혀 있었다. 저장소에 그대로 있는 문자열이라, 환경변수를 빠뜨린 채
 * 배포하면 <b>누구나 아무 사용자의 토큰이든 위조할 수 있다.</b> 로그인은 멀쩡히
 * 동작하므로 아무도 눈치채지 못한다.
 *
 * <p>개발용 값은 {@code application-test.yml} 과 {@code application-local.yml} 로 옮겼다.
 * 운영 프로필에는 기본값이 아예 없어야 한다.
 *
 * @param secret HS256 서명 키. 32바이트 미만이면 jjwt 가 거부하므로 여기서 먼저 잡는다
 */
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public record AuthProperties(
        @NotBlank(message = "JWT_SECRET 이 필요합니다. 값이 없으면 토큰을 위조할 수 있습니다.")
        @Size(min = 32, message = "JWT_SECRET 은 32바이트 이상이어야 합니다 (HS256 요구사항).")
        String secret,

        Duration accessTtl,
        Duration refreshTtl
) {}
