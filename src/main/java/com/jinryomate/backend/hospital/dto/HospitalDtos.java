package com.jinryomate.backend.hospital.dto;

import java.util.List;

/** 병원 검색의 응답. 화면 {@code 1m-B}. */
public final class HospitalDtos {

    private HospitalDtos() {}

    /**
     * 검색 결과 한 건.
     *
     * <p><b>이름과 주소를 낸다.</b> 처음에는 이름과 홈페이지를 냈는데 골라야 할 것을 잘못 골랐다 —
     * 화면이 그리는 것은 주소이고 홈페이지는 쓰는 자리가 없다. 무엇보다
     * <b>주소가 없으면 같은 이름의 다른 지점을 구별할 수 없다.</b> "○○의원"은 검색하면
     * 여러 곳이 같은 줄로 보인다.
     *
     * <p>심평원 응답에는 전화·좌표·의사 수까지 있지만 화면이 안 쓰는 것은 내지 않는다.
     * 지도가 들어오면 그때 좌표를 더한다.
     */
    public record Hospital(String name, String address) {}

    /**
     * @param totalCount 전체 건수. 앱이 "더 보기"를 띄울지 판단한다
     */
    public record HospitalSearchResponse(List<Hospital> hospitals, int totalCount) {}
}
