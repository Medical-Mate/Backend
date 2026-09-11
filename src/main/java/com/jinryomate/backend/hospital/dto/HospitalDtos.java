package com.jinryomate.backend.hospital.dto;

import java.util.List;

/** 병원 검색의 응답. 화면 {@code 1m-B}. */
public final class HospitalDtos {

    private HospitalDtos() {}

    /**
     * 검색 결과 한 건.
     *
     * <p><b>이름과 홈페이지만 낸다.</b> 심평원 응답에는 주소·전화·좌표·의사 수까지 들어 있지만,
     * 쓰지 않을 값을 내려보내면 앱이 무엇을 믿어야 할지 흐려진다. 필요해지면 그때 넓힌다.
     *
     * @param url 홈페이지. <b>없는 병원이 많다.</b> 특히 작은 의원은 대부분 비어 있어
     *            {@code null} 이 정상이다 — 화면에서 링크를 숨기면 된다
     */
    public record Hospital(String name, String url) {}

    /**
     * @param totalCount 전체 건수. 앱이 "더 보기"를 띄울지 판단한다
     */
    public record HospitalSearchResponse(List<Hospital> hospitals, int totalCount) {}
}
