package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinryomate.backend.auth.config.KakaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * 앱 ID 설정이 빠지면 <b>기동이 실패해야 한다.</b>
 *
 * <p>예전에는 값이 없으면 대조를 건너뛰었다. 방어선이 꺼진 채로 로그인이 멀쩡히 성공하니
 * 앱에서도 로그에서도 알 수가 없었다. 배포할 때 환경변수 하나 빠뜨리는 것만으로
 * 남의 앱 토큰이 통과하게 된다.
 *
 * <p>사람이 배포 전에 확인하는 것으로는 부족하다. 확인을 잊는 것이 바로 그 실패 경로다.
 */
class KakaoPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("kakao.api-base-url=https://kapi.kakao.com");

    @Test
    @DisplayName("앱 ID가 없으면 기동하지 않는다")
    void 앱_ID_없으면_기동_실패() {
        // 검증 메시지는 최상위가 아니라 근본 원인에 담긴다.
        // 기동을 막는 것만으로는 부족하고, 무엇이 빠졌는지 읽혀야 한다.
        runner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("KAKAO_APP_ID"));
    }

    @Test
    @DisplayName("환경변수를 비워둔 것도 없는 것으로 본다")
    void 빈_값도_실패() {
        // application.yml 이 `app-id: ${KAKAO_APP_ID:}` 라, 환경변수를 안 주면
        // 빈 문자열이 들어온다. Long 으로는 null 이 되므로 여기서 걸려야 한다.
        runner.withPropertyValues("kakao.app-id=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("앱 ID가 있으면 정상적으로 뜬다")
    void 앱_ID_있으면_기동() {
        runner.withPropertyValues("kakao.app-id=111111")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .getBean(KakaoProperties.class)
                        .extracting(KakaoProperties::appId)
                        .isEqualTo(111111L));
    }

    @Configuration
    @EnableConfigurationProperties(KakaoProperties.class)
    static class TestConfig {}
}
