package com.jinryomate.backend.demo.web;

import com.jinryomate.backend.demo.service.DemoEventCatalog;
import com.jinryomate.backend.demo.service.DemoEventRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 데모 요청 한 건당 한 줄.
 *
 * <p><b>백엔드가 보여줄 셋이 여기서 나옵니다</b> — 트래픽(개수), 응답속도(p50·p95),
 * 에러율(상태 분포). Caddy 액세스 로그에 같은 것이 찍히지만 텍스트라 조회가 안 됩니다.
 *
 * <p><b>제일 값어치 있는 건 뺄셈입니다.</b>
 *
 * <pre>
 *   web.request.duration_ms  −  ai.responded.latency_ms  =  우리가 쓴 시간
 * </pre>
 *
 * "느린 게 우리인지 Bedrock 인지" 가 여기서 갈립니다. 이 줄이 없으면 턴 하나가 797ms 인
 * 것만 알지, 그게 통째로 AI 때문인지 우리가 보태고 있는지 말할 수 없습니다.
 *
 * <p><b>429 는 남기지 않습니다.</b> {@link com.jinryomate.backend.demo.service.DemoRateLimiter}
 * 가 이미 {@code guard.rate_limited} 로 세고 있어 두 번 세는 셈이고, 더 중요하게는
 * <b>429 는 상한을 넘어선 요청마다 나가므로 개수에 천장이 없습니다.</b> 그걸 다 기록하면
 * 공격을 우리가 증폭합니다 — 상대는 요청 한 번, 우리는 INSERT 한 번. 빼고 나면 이 이벤트는
 * 빈도 제한이 통과시킨 것만 남아 <b>200/분에 자연히 묶입니다.</b>
 *
 * <p><b>{@link com.jinryomate.backend.global.web.RequestIdFilter} 바로 다음입니다.</b> 요청
 * id 가 채워진 뒤여야 기록이 백엔드 로그와 이어지고, {@link DemoBodySizeFilter} 보다
 * 바깥이어야 413 도 잽니다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class DemoRequestMetricsFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/demo/";

    /** 300초를 넘는 요청은 카탈로그가 안 받는다. 그쯤이면 어차피 끊긴 것이다. */
    private static final long MAX_MS = 300_000L;

    private final DemoEventRecorder recorder;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long began = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // finally 에서 잰다. 예외로 빠져나가는 길(413·502)도 재야 에러율이 맞는다.
            record(request, response.getStatus(), (System.nanoTime() - began) / 1_000_000L);
        }
    }

    private void record(HttpServletRequest request, int status, long tookMs) {
        if (status == 429) {
            return;
        }
        Map<String, Object> props = new HashMap<>(3);
        props.put("status", status);
        props.put("duration_ms", Math.min(tookMs, MAX_MS));

        // 경로는 카탈로그의 닫힌 목록으로 좁힌다. 요청 URI 를 그대로 넣으면 목록 밖 값이라
        // 기록이 통째로 떨어진다 — 모르는 경로면 경로 없이 남긴다.
        String rest = request.getRequestURI().substring(
                request.getRequestURI().indexOf(PREFIX) + PREFIX.length());
        if (DemoEventCatalog.knowsDemoPath(rest)) {
            props.put("path", rest);
        }

        recorder.record("web.request", props);
    }
}
