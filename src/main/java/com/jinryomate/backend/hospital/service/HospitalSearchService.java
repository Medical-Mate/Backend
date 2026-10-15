package com.jinryomate.backend.hospital.service;

import com.jinryomate.backend.hospital.client.HospitalSearchClient;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import com.jinryomate.backend.hospital.query.HospitalQueryResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 병원 검색. 검색어를 풀고({@link HospitalQueryResolver}) 푼 결과로 찾는다({@link HospitalSearchClient}).
 *
 * <p>앱 경로와 웹 데모 경로가 같이 쓴다. 둘이 다른 결과를 내면 안 된다.
 */
@Service
@RequiredArgsConstructor
public class HospitalSearchService {

    private final HospitalQueryResolver resolver;
    private final HospitalSearchClient client;

    public HospitalSearchResponse search(String query, int page, int size) {
        return client.search(resolver.resolve(query), page, size);
    }
}
