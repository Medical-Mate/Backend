package com.jinryomate.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API 문서 설정.
 *
 * <p>문서 경로 자체는 {@link SecurityConfig}가 <b>개발 프로필에서만</b> 연다.
 * 진료 API의 전체 구조를 운영에서 공개할 이유가 없다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("진료메이트 API")
                        .version("v1")
                        .description("""
                                환자가 진료 전 AI 문답으로 증상을 정리해 브리핑 카드를 만들고,
                                진료실에서 의사에게 전달하는 앱의 백엔드 API입니다.

                                ### 인증

                                `POST /api/auth/kakao` 로 받은 `accessToken` 을 우측 상단 **Authorize** 에 넣으면
                                보호된 API를 여기서 바로 호출할 수 있습니다. `Bearer` 는 빼고 토큰만 넣으세요.

                                ### 오류 응답

                                모든 오류는 같은 형태입니다.

                                ```json
                                {
                                  "error": { "code": "UPSTREAM_TIMEOUT", "message": "...", "retryable": true },
                                  "meta": { "requestId": "req_8f21c4" }
                                }
                                ```

                                `retryable` 이 `true` 면 재시도해도 되는 오류입니다.
                                `meta.requestId` 는 응답 헤더 `X-Request-Id` 와 같은 값이고,
                                문의할 때 이 값을 알려주시면 서버 로그를 바로 찾을 수 있습니다.
                                """))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("로그인 응답의 accessToken")));
    }
}
