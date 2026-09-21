package com.jinryomate.backend.demo.service;

import com.jinryomate.backend.demo.dto.DemoEventDtos.EventBatch;
import com.jinryomate.backend.demo.dto.DemoEventDtos.Item;
import com.jinryomate.backend.demo.entity.DemoEvent;
import com.jinryomate.backend.demo.repository.DemoEventRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 웹 데모 이벤트를 받아 저장한다.
 *
 * <p><b>동기로 넣습니다.</b> 이 경로는 문답 밖이고 브라우저는 {@code sendBeacon} 으로
 * 던지고 응답을 안 기다립니다. 50행 INSERT 는 밀리초 단위라 비동기 큐를 만들 이유가
 * 없습니다 — 큐를 두면 유실 정책과 백프레셔가 따라붙고, 그 복잡도로 얻는 게 없습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoEventService {

    /**
     * 기기 시계가 틀어져도 이 정도는 받습니다.
     *
     * <p>넘으면 시계가 고장났거나 누가 과거·미래로 밀어 넣는 중입니다. 대시보드에서
     * 엉뚱한 날짜에 봉우리가 서는 것보다 그 자리에서 막는 편이 낫습니다.
     */
    private static final Duration SKEW = Duration.ofHours(24);

    private final DemoEventRepository repository;

    /**
     * 묶음 하나를 받는다.
     *
     * <p><b>하나라도 규격 밖이면 묶음 전체를 거부합니다.</b> 좋은 것만 골라 넣으면
     * 프론트의 버그가 계속 숨습니다 — 클라이언트 버그는 체계적이라 모든 묶음에서
     * 같은 이벤트가 떨어지고, 통째로 400 이 나야 그날 안에 눈에 띕니다.
     *
     * @return 저장한 개수
     */
    @Transactional
    public int accept(EventBatch batch) {
        Instant now = Instant.now();
        List<DemoEvent> rows = new ArrayList<>(batch.events().size());

        for (Item item : batch.events()) {
            Optional<String> bad = DemoEventCatalog.check(item.event(), item.props());
            if (bad.isPresent()) {
                // 이유에는 이벤트 이름과 키까지만 있습니다. **값은 절대 싣지 않습니다** —
                // 그 자체가 새면 안 되는 것일 수 있어서 로그에도 안 남깁니다.
                log.warn("데모 이벤트 거부: {}", bad.get());
                throw new ApiException(ErrorCode.INVALID_REQUEST, bad.get());
            }

            Instant at = item.occurredAt().toInstant();
            if (at.isBefore(now.minus(SKEW)) || at.isAfter(now.plus(SKEW))) {
                log.warn("데모 이벤트 시각 이상: {}", item.event());
                throw new ApiException(ErrorCode.INVALID_REQUEST,
                        "이벤트 시각이 현재와 24시간 이상 벌어졌습니다: " + item.event());
            }

            rows.add(DemoEvent.of(item.event(), at, item.sessionId(), item.seq(),
                    item.surface(), item.build(), item.props()));
        }

        repository.saveAll(rows);
        return rows.size();
    }
}
