package com.jinryomate.backend.hospital.client;

import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import com.jinryomate.backend.hospital.query.HospitalQuery;

/**
 * 병원을 찾는다. 화면 {@code 1m-B}.
 *
 * <p>검색어를 푸는 것은 {@link com.jinryomate.backend.hospital.query.HospitalQueryResolver} 가 하고,
 * 여기는 푼 결과로 찾기만 한다.
 *
 * <p>심평원 병원정보서비스가 원천이다. <b>우리가 병원 목록을 들고 있지 않는다</b> —
 * 개원·폐원이 계속 생기는 데이터라 복사해 두면 바로 낡는다.
 */
public interface HospitalSearchClient {

    /**
     * @param query 이름 일부(부분 일치)와 지역 코드
     * @param page  1부터
     */
    HospitalSearchResponse search(HospitalQuery query, int page, int size);
}
