package com.jinryomate.backend.auth.web;

import com.jinryomate.backend.auth.service.JwtProvider;
import com.jinryomate.backend.global.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer 토큰이 있으면 인증 주체를 SecurityContext에 담는다.
 *
 * <p>토큰이 없거나 틀려도 여기서 응답을 만들지 않는다. 그 판단은 SecurityConfig의
 * 경로 규칙이 한다 — 무인증으로 열린 경로가 토큰 오류로 막히면 안 되기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER)) {
            try {
                Long userId = jwtProvider.parseUserId(header.substring(BEARER.length()));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (ApiException e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
