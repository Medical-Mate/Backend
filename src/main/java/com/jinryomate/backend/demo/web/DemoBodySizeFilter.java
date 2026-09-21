package com.jinryomate.backend.demo.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.demo.service.DemoEventCatalog;
import com.jinryomate.backend.demo.service.DemoEventRecorder;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.global.error.ErrorResponse;
import com.jinryomate.backend.global.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 데모 경로의 요청 본문 크기 상한.
 *
 * <p><b>왜 필요한가</b> — {@code /api/demo/**} 는 인증이 없고, {@link DemoController} 가
 * 본문을 {@code @RequestBody byte[]} 로 <b>통째로 메모리에 올린다.</b> 빈도 제한
 * ({@link com.jinryomate.backend.demo.service.DemoRateLimiter})은 <b>횟수만 막고 크기는
 * 막지 않는다</b> — 분당 30회 안에서도 큰 본문을 보내면 2GB 짜리 운영 머신이 눕는다.
 *
 * <p><b>{@code Content-Length} 만 보면 안 된다.</b> {@code Transfer-Encoding: chunked} 로
 * 보내면 {@link HttpServletRequest#getContentLengthLong()} 이 -1 이라 그대로 통과한다.
 * 그러면 막고 있다고 믿는데 실제로는 안 막히는 상태가 된다 — 이 저장소가 제일 경계하는
 * 모양이다. 그래서 헤더를 먼저 보고, 없으면 <b>읽는 동안 세어서</b> 넘는 순간 끊는다.
 *
 * <p><b>왜 Caddy 가 아니라 여기인가</b> — 리버스 프록시에서 막는 것이 더 앞이라 좋지만,
 * {@code docker-compose.prod.yml} 과 Caddyfile 은 <b>저장소에 없고 서버에만 있다.</b>
 * 저장소만 보고는 막혀 있는지 알 수 없고 서버를 새로 올리면 빠진다. 여기 두면 코드와
 * 테스트가 함께 따라온다. 프록시 쪽은 나중에 한 겹 더 두면 된다.
 *
 * <p><b>데모에만 건다.</b> 앱 경로는 계정 뒤에 있고 본문 모양이 달라 같은 값을 걸 근거가
 * 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoBodySizeFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    /** 막힌 횟수를 남긴다. 브라우저는 자기가 막힌 것만 안다. */
    private final DemoEventRecorder recorder;

    /**
     * 1MB.
     *
     * <p>데모 본문에서 제일 큰 것은 AI 의 {@code state} 인데 JSON 텍스트라 11턴을 해도
     * 수십 KB 수준이다. 20배 넘는 여유를 두면서 메모리를 지키는 값이다.
     *
     * <p><b>좁게 잡으면 정상 문답이 막힌다.</b> 그것도 이 필터가 막으려는 것과 같은
     * 종류의 사고라, 넉넉한 쪽으로 정했다.
     */
    public static final long MAX_BYTES = 1024L * 1024L;

    private static final String PREFIX = "/api/demo/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > MAX_BYTES) {
            // 본문을 읽기도 전에 끊는다. 헤더만 보고 판단할 수 있는 경우다.
            //
            // **여기서는 던지지 않고 직접 쓴다.** 필터는 DispatcherServlet 앞이라
            // @RestControllerAdvice 가 잡지 못한다 — 던지면 413 이 아니라 500 이 나간다.
            log.warn("데모 본문 크기 초과 declared={}", declared);
            record(request);
            writeTooLarge(response);
            return;
        }
        // 길이를 안 밝힌 요청(chunked)은 읽으면서 센다.
        chain.doFilter(new LimitedRequest(request), response);
    }

    /**
     * 막힌 것을 남긴다.
     *
     * <p>경로는 카탈로그의 닫힌 목록으로 좁힌다 — 요청 URI 를 그대로 넣으면 목록 밖
     * 값이라 기록이 통째로 떨어진다.
     */
    private void record(HttpServletRequest request) {
        String rest = request.getRequestURI().substring(
                request.getRequestURI().indexOf(PREFIX) + PREFIX.length());
        recorder.record("guard.payload_too_large",
                DemoEventCatalog.knowsDemoPath(rest) ? Map.of("path", rest) : Map.of());
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.PAYLOAD_TOO_LARGE.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(
                ErrorCode.PAYLOAD_TOO_LARGE,
                ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage(),
                RequestIdFilter.current()));
    }

    /**
     * 본문 스트림을 감싸 누적 바이트를 센다.
     *
     * <p>여기서 던지는 것은 <b>DispatcherServlet 안</b>(메시지 컨버터가 읽는 중)이라
     * {@code @RestControllerAdvice} 가 잡아 413 으로 나간다. 헤더로 걸리는 경우와
     * 경로가 다르다.
     */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                private void count(int n) {
                    if (n < 0) {
                        return;
                    }
                    read += n;
                    if (read > MAX_BYTES) {
                        log.warn("데모 본문 크기 초과 read={}", read);
                        throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
                    }
                }

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    count(b < 0 ? -1 : 1);
                    return b;
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    int n = delegate.read(b, off, len);
                    count(n);
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
