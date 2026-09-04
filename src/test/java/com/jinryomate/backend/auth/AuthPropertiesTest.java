package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinryomate.backend.auth.config.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * JWT 시크릿이 없으면 <b>기동이 실패해야 한다.</b>
 *
 * <p>예전에는 {@code application.yml} 에 개발용 기본값이 박혀 있었다. 저장소에 그대로
 * 있는 문자열이라, 환경변수를 빠뜨린 채 배포하면 그걸 아는 누구나 <b>아무 사용자의
 * 토큰이든 위조</b>할 수 있다. 로그인은 멀쩡히 동작하므로 아무도 눈치채지 못한다.
 *
 * <p>카카오 앱 ID({@link KakaoPropertiesTest})와 같은 종류의 문제이고, 이쪽이 더 위험하다.
 * 앱 ID 는 남의 앱 토큰이 통과하는 정도지만, 이건 로그인 자체가 무의미해진다.
 */
class AuthPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("auth.jwt.access-ttl=1h", "auth.jwt.refresh-ttl=30d");

    @Test
    @DisplayName("시크릿이 없으면 기동하지 않는다")
    void 시크릿_없으면_기동_실패() {
        runner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("JWT_SECRET"));
    }

    @Test
    @DisplayName("환경변수를 비워둔 것도 없는 것으로 본다")
    void 빈_값도_실패() {
        runner.withPropertyValues("auth.jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("32바이트 미만이면 기동하지 않는다")
    void 짧은_시크릿_거부() {
        // HS256 은 256비트(32바이트) 이상을 요구한다. jjwt 가 런타임에 거부하는데,
        // 그때는 이미 배포된 뒤라 첫 로그인에서야 드러난다. 기동 시점에 잡는다.
        runner.withPropertyValues("auth.jwt.secret=too-short")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("32바이트"));
    }

    @Test
    @DisplayName("충분히 긴 시크릿이면 정상적으로 뜬다")
    void 정상_시크릿() {
        runner.withPropertyValues("auth.jwt.secret=a-sufficiently-long-signing-key-32bytes+")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration
    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfig {}
}
