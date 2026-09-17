package com.jinryomate.backend.demo.service;

import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 데모 경로의 호출 빈도 제한.
 *
 * <p><b>왜 우리가 거는가</b> — AI 는 프록시 뒤에 있어 요청 IP 가 전부 백엔드로 보인다.
 * 거기서 걸면 아무도 구별하지 못한다. 공개 엣지가 우리라 여기가 유일한 자리다.
 *
 * <p><b>둘을 함께 건다.</b>
 *
 * <ul>
 *   <li><b>IP 별</b> — 평범한 남용을 막는다. 다만 {@code X-Forwarded-For} 는 앞쪽을
 *       클라이언트가 지어낼 수 있어 <b>최선을 다하는 수준</b>이다
 *   <li><b>전체</b> — IP 를 바꿔 가며 때려도 총량이 막힌다. 크레딧을 지키는 것은
 *       사실상 이쪽이다
 * </ul>
 *
 * <p>IP 제한만 두면 "막고 있다"고 믿으면서 실제로는 안 막히는 상태가 된다. 헤더를 믿을 수
 * 없는데 그걸 유일한 방어선으로 두지 않는다.
 *
 * <p><b>메모리에 둔다.</b> 인스턴스가 하나뿐이고 3주 일정이다. 라이브러리를 붙이면 의존성이
 * 늘고, 재시작하면 카운터가 0 이 되는 것은 AI 의 일일 상한도 마찬가지다 — 마지막 방어선은
 * 어차피 AWS 예산 알림이다.
 */
@Slf4j
@Component
public class DemoRateLimiter {

    /** 한 IP 가 1분에 보낼 수 있는 요청 수. 문답 한 세션이 6턴 남짓이라 넉넉하다. */
    private static final int PER_IP_PER_MINUTE = 30;

    /**
     * 전체가 1분에 보낼 수 있는 요청 수.
     *
     * <p>AI 일일 상한이 $2(하루 100세션 남짓)이다. 분당 200이면 한 사람이 몰아쳐도
     * 그 상한에 닿기 전에 여기서 먼저 걸린다.
     */
    private static final int TOTAL_PER_MINUTE = 200;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    /** 창이 넘어갈 때 통째로 비우므로 커지지 않는다. */
    private final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private final AtomicInteger total = new AtomicInteger();

    private volatile Instant windowStartedAt = Instant.now();

    /** 넘으면 429 를 던진다. */
    public void check(HttpServletRequest request) {
        rollWindow();

        if (total.incrementAndGet() > TOTAL_PER_MINUTE) {
            log.warn("데모 전체 호출 제한 초과");
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }

        String ip = clientIp(request);
        int used = perIp.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
        if (used > PER_IP_PER_MINUTE) {
            // IP 는 개인정보라 통째로 남기지 않는다. 몇 번째인지만 남긴다.
            log.warn("데모 IP 호출 제한 초과 count={}", used);
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    /**
     * 창을 비운다. <b>테스트 전용이다.</b>
     *
     * <p>창이 정적 상태라 한 테스트가 상한을 쓰면 다음 테스트가 429 를 맞는다. 그렇게
     * 서로를 오염시키면 "빈도 제한이 걸린다"가 아니라 "앞 테스트가 많이 불렀다"를
     * 검증하게 된다. 운영 코드에서는 아무도 부르지 않는다.
     */
    public void reset() {
        perIp.clear();
        total.set(0);
        windowStartedAt = Instant.now();
    }

    private synchronized void rollWindow() {
        if (Duration.between(windowStartedAt, Instant.now()).compareTo(WINDOW) < 0) {
            return;
        }
        perIp.clear();
        total.set(0);
        windowStartedAt = Instant.now();
    }

    /**
     * 요청을 보낸 쪽을 가른다.
     *
     * <p>CloudFront → Caddy → 우리 순서라 {@code X-Forwarded-For} 의 <b>맨 앞</b>이 실제
     * 사용자다. 다만 그 값은 클라이언트가 지어낼 수 있다 — 그래서 전체 제한을 따로 둔다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
