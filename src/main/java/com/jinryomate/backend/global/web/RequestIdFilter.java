package com.jinryomate.backend.global.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 requestId를 부여해 MDC와 응답 헤더에 싣는다.
 *
 * <p>AI 서비스 호출 시 이 값을 그대로 넘기고 응답의 meta.requestId로 돌려받는다.
 * 장애가 났을 때 백엔드 로그와 AI 로그를 잇는 유일한 열쇠다.
 *
 * <p><b>맨 앞에 둔다.</b> 기본 순서로 두면 스프링 시큐리티 체인
 * ({@code SecurityProperties.DEFAULT_FILTER_ORDER}, -100)이 먼저 돌아서,
 * 인증에서 잘린 401 요청은 여기까지 오지 못한다. 그러면 추적이 가장 필요한
 * 응답에만 requestId가 없다.
 *
 * <p>시큐리티 바로 앞(-101)이 아니라 맨 앞인 이유는, 나중에 그보다 앞서는
 * 필터가 생겼을 때 또 비는 경로가 생기기 때문이다. requestId는 모든 요청에
 * 붙어야 하므로 앞설수록 좋고, 뒤에 둬서 얻는 것이 없다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
