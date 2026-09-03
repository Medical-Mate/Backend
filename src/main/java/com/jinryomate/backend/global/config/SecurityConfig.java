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

                .authorizeHttpRequests(auth -> auth
                        // --- 무인증으로 여는 경로 ---
                        .requestMatchers("/api/auth/kakao", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        // 공유 링크(S6). 인증 대신 토큰 만료가 방어선이다.
                        .requestMatchers("/s/**").permitAll()

                        // --- 나머지는 전부 인증 필요 ---
                        .anyRequest().authenticated())

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
