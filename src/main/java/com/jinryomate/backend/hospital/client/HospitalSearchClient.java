package com.jinryomate.backend.hospital.client;

import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;

/**
 * 병원 이름으로 찾는다. 화면 {@code 1m-B}.
 *
 * <p>심평원 병원정보서비스가 원천이다. <b>우리가 병원 목록을 들고 있지 않는다</b> —
 * 개원·폐원이 계속 생기는 데이터라 복사해 두면 바로 낡는다.
 */
public interface HospitalSearchClient {

    /**
     * @param query 병원 이름 일부. 부분 일치한다({@code 서울} → 4339건)
     * @param page  1부터
     */
    HospitalSearchResponse search(String query, int page, int size);
}
