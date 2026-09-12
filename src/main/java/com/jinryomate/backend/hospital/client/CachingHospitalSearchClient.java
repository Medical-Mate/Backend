package com.jinryomate.backend.hospital.client;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

/**
 * 병원 검색 결과를 잠시 들고 있는다.
 *
 * <p>두 가지를 동시에 푼다.
 *
 * <ul>
 *   <li><b>느리다</b> — 상류 실측 중앙값이 1.6초다. 검색어를 한 자 칠 때마다 기다리는 화면이라
 *       체감이 크다. 같은 질의가 대부분이라 캐시가 잘 듣는다
 *   <li><b>일일 할당량</b> — 시험 호출 스무 번 남짓으로 넘긴 적이 있다. 상류 호출 수를 줄이는
 *       것이 가장 직접적인 대책이다
 * </ul>
 *
 * <p><b>실패는 캐시하지 않는다.</b> 예외가 나면 값이 들어가지 않으므로 다음 요청이 다시 시도한다.
 *
 * <p><b>0건은 짧게 붙든다.</b> 상류가 성공 모양으로 빈 결과를 줄 가능성을 배제할 수 없어서,
 * 그런 응답을 몇 시간 들고 있으면 상류가 살아난 뒤에도 계속 "결과 없음"을 준다. 오타로 0건이
 * 나오는 정상 경우는 짧은 TTL 로도 충분히 막아진다.
 */
@Slf4j
public class CachingHospitalSearchClient implements HospitalSearchClient {

    /** 결과가 있는 질의. 병원 목록은 하루 사이에 바뀌지 않는다. */
    private static final Duration HIT_TTL = Duration.ofHours(6);

    /** 0건. 상류 실패를 오래 붙들지 않으려고 짧게 둔다. */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    private static final int MAX_ENTRIES = 1_000;

    private final HospitalSearchClient delegate;
    private final Cache<String, HospitalSearchResponse> cache;

    public CachingHospitalSearchClient(HospitalSearchClient delegate) {
        this.delegate = delegate;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfter(new Expiry<String, HospitalSearchResponse>() {
                    @Override
                    public long expireAfterCreate(String key, HospitalSearchResponse value, long now) {
                        Duration ttl = value.hospitals().isEmpty() ? EMPTY_TTL : HIT_TTL;
                        return ttl.toNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, HospitalSearchResponse value,
                                                  long now, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(String key, HospitalSearchResponse value,
                                                long now, long currentDuration) {
                        // 읽었다고 수명을 늘리지 않는다. 인기 질의가 영원히 안 늙으면 안 된다.
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public HospitalSearchResponse search(String query, int page, int size) {
        // 검색어는 증상이 아니라 병원 이름이라 로그에 남겨도 된다. 그래도 키에만 쓴다.
        String key = query + "|" + page + "|" + size;

        HospitalSearchResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // 예외가 나면 여기서 그대로 올라가고 캐시에는 아무것도 안 들어간다.
        HospitalSearchResponse fresh = delegate.search(query, page, size);
        cache.put(key, fresh);
        return fresh;
    }
}
