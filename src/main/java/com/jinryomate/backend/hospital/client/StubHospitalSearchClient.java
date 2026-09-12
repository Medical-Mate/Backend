package com.jinryomate.backend.hospital.client;

import com.jinryomate.backend.hospital.dto.HospitalDtos.Hospital;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import java.util.List;

/**
 * 서비스 키가 없을 때 쓰는 임시 구현.
 *
 * <p>로컬 개발과 테스트에서는 키가 없는 것이 정상이다. 키 없이 실제로 부르면 심평원이
 * <b>인증 오류가 아니라 0건</b>을 돌려줘서, 검색이 안 되는 건지 결과가 없는 건지 구별되지 않는다.
 * 그럴 바엔 대놓고 가짜를 주는 편이 낫다.
 *
 * <p>값은 가짜지만 <b>모양은 진짜여야 한다</b> — 주소가 없는 병원을 섞어 둔 이유다.
 */
public class StubHospitalSearchClient implements HospitalSearchClient {

    private static final List<Hospital> FIXTURES = List.of(
            new Hospital("서울대학교병원", "서울특별시 종로구 대학로 101"),
            new Hospital("분당서울대학교병원", "경기도 성남시 분당구 구미로173번길 82"),
            new Hospital("연세세브란스병원", "서울특별시 서대문구 연세로 50-1"),
            // 주소가 없는 경우. 드물지만 화면이 이 상태를 견뎌야 한다.
            new Hospital("행복한의원", null));

    @Override
    public HospitalSearchResponse search(String query, int page, int size) {
        List<Hospital> matched = FIXTURES.stream()
                .filter(h -> query == null || query.isBlank() || h.name().contains(query))
                .toList();

        int from = Math.min((page - 1) * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new HospitalSearchResponse(matched.subList(from, to), matched.size());
    }
}
