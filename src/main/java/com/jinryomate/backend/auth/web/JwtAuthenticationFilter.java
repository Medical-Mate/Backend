package com.jinryomate.backend.auth.web;

import com.jinryomate.backend.auth.repository.UserRepository;
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
 *
 * <p><b>서명뿐 아니라 사용자가 아직 있는지도 확인한다.</b> JWT는 무상태라 한 번 나가면
 * 만료까지 회수할 수 없다. 탈퇴하면 refresh는 지워져 재발급이 막히지만, 이미 발급된
 * 액세스 토큰은 수명(1시간)만큼 그대로 통한다. 탈퇴는 "지금 끊어달라"는 요청인데
 * 한 시간 뒤에 끊기면 그 요청을 절반만 들어준 것이다.
 *
 * <p>기본키 조회 한 번을 인증 요청마다 더 쓴다. 데모 규모에서 부담이 없고,
 * "탈퇴했는데 아직 들어가진다"는 상태를 남기지 않는 값이 그보다 크다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER)) {
            try {
                Long userId = jwtProvider.parseUserId(header.substring(BEARER.length()));

                // 탈퇴한 사용자의 토큰은 서명이 멀쩡해도 통하지 않는다.
                if (userRepository.existsById(userId)) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(userId, null, List.of()));
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (ApiException e) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
