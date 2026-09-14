package com.jinryomate.backend.global.config;

import com.jinryomate.backend.auth.web.JwtAuthenticationFilter;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.global.error.ErrorResponse;
import com.jinryomate.backend.global.web.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 인가 규칙을 한 곳에 모은다.
 *
 * <p>무인증으로 열리는 경로와 보호해야 하는 경로가 섞여 있어서, 필터 안에 예외 경로를
 * 흩어두는 대신 여기 한 곳에서 선언한다. 빠뜨린 경로가 눈에 보이게 하려는 목적이다.
 *
 * <p>새 API를 추가할 때 <b>기본은 인증 필요</b>다. 무인증으로 열려면 여기에 명시적으로 적는다.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * API 문서 경로. 모든 프로필에서 연다.
     *
     * <p>예전에는 {@code local}·{@code dev} 에서만 열었다. 앱·AI 담당자가 배포된
     * 서버를 보고 붙어야 하고, 발표에서도 API 를 보여줘야 해서 운영에서도 연다.
     *
     * <p>문서가 열려도 <b>엔드포인트는 여전히 JWT 를 요구한다.</b> 드러나는 것은
     * 어떤 API 가 있는지이지 데이터가 아니다. 그 대가로 얻는 것이 더 크다고 봤다.
     */
    private static final String[] DOCS_PATHS = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 토큰 기반이라 세션·CSRF·기본 로그인 폼이 모두 불필요하다.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> {
                    // --- 무인증으로 여는 경로 ---
                    auth.requestMatchers("/api/auth/kakao", "/api/auth/refresh").permitAll();
                    auth.requestMatchers("/api/health", "/actuator/health").permitAll();

                    // 카카오 연결 해제 웹훅. 카카오는 우리 JWT 를 갖고 있지 않다.
                    // 대신 컨트롤러가 어드민 키와 app_id 를 대조한다 — 그게 유일한 방어선이다.
                    auth.requestMatchers("/webhooks/kakao/**").permitAll();

                    // API 문서. 운영 포함 모든 환경에서 연다.
                    auth.requestMatchers(DOCS_PATHS).permitAll();

                    // 웹 데모. 스토어 제출이 불가해 만든 한시 경로이고 심사가 끝나면
                    // 이 줄과 demo 패키지를 지운다.
                    //
                    // 인증이 없는 대신 컨트롤러가 호출 빈도를 제한하고, 저장을 하지 않아
                    // 남는 것이 없다. 문진만 열려 있어 카드·기록·일정은 계정이 있어야 한다.
                    auth.requestMatchers("/api/demo/**").permitAll();

                    // --- 나머지는 전부 인증 필요 ---
                    auth.anyRequest().authenticated();
                })

                .exceptionHandling(e -> e.authenticationEntryPoint(this::writeUnauthorized))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 인증 실패도 다른 오류와 같은 형태로 응답한다. 앱이 분기를 하나만 두면 되도록. */
    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                   HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException e)
            throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                ErrorCode.UNAUTHORIZED,
                "로그인이 필요합니다.",
                RequestIdFilter.current()));
    }
}
