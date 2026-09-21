package com.jinryomate.backend.demo.service;

import com.jinryomate.backend.demo.entity.DemoEvent;
import com.jinryomate.backend.demo.repository.DemoEventRepository;
import com.jinryomate.backend.global.web.RequestIdFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서버가 아는 값을 이벤트로 남긴다.
 *
 * <p><b>브라우저는 자기가 막힌 것만 압니다.</b> 전체 200회/분에 걸려 떨어진 사람이 몇인지,
 * AI 응답이 실제로 얼마나 걸렸는지, 예산이 몇 시에 말랐는지는 서버만 압니다. 웹은 이미
 * PostHog 로 화면 흐름을 보고 있으니, 여기서는 <b>거기서 안 보이는 것만</b> 넣습니다.
 *
 * <p><b>요청을 절대 실패시키지 않습니다.</b> 이 클래스의 모든 실패는 삼킵니다 — 기록이
 * 안 남는 것보다 기록 때문에 문답이 막히는 쪽이 훨씬 나쁩니다.
 *
 * <p><b>화이트리스트를 똑같이 통과합니다.</b> 서버가 쓰는 길이라고 느슨해지면 카탈로그를
 * 둔 의미가 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoEventRecorder {

    /**
     * 분당 상한.
     *
     * <p><b>{@code guard.rate_limited} 는 하필 두들겨 맞을 때 터지는 이벤트입니다.</b>
     * 막을 때마다 한 줄씩 쓰면 공격을 우리가 증폭합니다 — 상대는 요청 한 번, 우리는
     * INSERT 한 번. 넘으면 조용히 버립니다. 이벤트는 잃어도 되는 데이터입니다.
     *
     * <p><b>정상 트래픽보다는 넉넉해야 합니다.</b> 요청 한 건당 {@code web.request} 가
     * 한 줄 나가는데 전체 호출 상한이 200회/분이라, 상한을 그보다 낮게 잡으면 공격을
     * 막는 게 아니라 <b>평소 지표가 새 버립니다.</b> 300 이면 정상 트래픽을 다 담고도
     * 남습니다.
     */
    private static final int MAX_PER_MINUTE = 300;

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final DemoEventRepository repository;

    private final AtomicInteger written = new AtomicInteger();
    private volatile Instant windowStartedAt = Instant.now();

    /**
     * 한 줄 남긴다.
     *
     * <p>{@code session_id} 는 <b>요청 id</b> 다. 브라우저 세션이 없고, 이 값이면 백엔드
     * 로그와 한 줄로 이어집니다.
     *
     * <p><b>트랜잭션을 따로 엽니다.</b> {@code guard.*} 와 {@code upstream.failed} 는
     * <b>예외를 던지는 길</b>에서 부릅니다 — 부르는 쪽 트랜잭션에 얹으면 롤백될 때
     * 막힌 기록만 쏙 빠집니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event, Map<String, Object> props) {
        try {
            if (!allow()) {
                return;
            }

            Optional<String> bad = DemoEventCatalog.check(event, props);
            if (bad.isPresent()) {
                // 우리 코드의 버그다. 요청을 죽이지는 않되 눈에 띄게 남긴다.
                log.warn("서버 이벤트가 카탈로그를 벗어났다: {}", bad.get());
                return;
            }

            String requestId = RequestIdFilter.current();
            repository.save(DemoEvent.of(
                    event,
                    Instant.now(),
                    session(requestId),
                    1,
                    "server",
                    null,
                    props));

        } catch (Exception e) {
            // 기록 때문에 요청이 죽으면 안 된다. 종류만 남긴다 — 메시지에 본문이 실릴 수 있다.
            log.warn("서버 이벤트 기록 실패 event={} type={}", event, e.getClass().getSimpleName());
        }
    }

    /** 속성 없는 이벤트. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event) {
        record(event, Map.of());
    }

    /**
     * 요청 id 를 세션 키 모양으로 맞춘다.
     *
     * <p>카탈로그가 {@code [A-Za-z0-9_-]{8,32}} 를 받는다. 요청 id 가 없는 경로(스케줄러
     * 같은 것)에서는 고정값을 쓴다 — 그런 줄은 어차피 한 요청에 묶일 것이 없다.
     */
    private static String session(String requestId) {
        if (requestId == null || requestId.length() < 8) {
            return "server__nored";
        }
        return requestId.length() > 32 ? requestId.substring(0, 32) : requestId;
    }

    /** 분당 상한. 창이 지나면 비운다. */
    private synchronized boolean allow() {
        Instant now = Instant.now();
        if (Duration.between(windowStartedAt, now).compareTo(WINDOW) >= 0) {
            windowStartedAt = now;
            written.set(0);
        }
        return written.incrementAndGet() <= MAX_PER_MINUTE;
    }

    /**
     * 창을 비운다. <b>테스트 전용이다.</b>
     *
     * <p>{@link DemoRateLimiter#reset()} 과 같은 이유다 — 정적 상태라 한 테스트가 상한을
     * 쓰면 다음 테스트가 조용히 아무것도 안 쓴다.
     */
    public void reset() {
        written.set(0);
        windowStartedAt = Instant.now();
    }
}
