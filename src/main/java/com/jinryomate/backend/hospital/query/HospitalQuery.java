package com.jinryomate.backend.hospital.query;

/**
 * 검색어를 푼 결과. 심평원에 무엇을 거를지다.
 *
 * <p>{@code 노원 피부과} → {@code (피부과, 110000, 110022)}. 지역을 못 풀면 검색어 전체가
 * {@code name} 으로 들어간다 — 지금까지 하던 이름 검색과 같다.
 *
 * @param name    이름 부분 일치({@code yadmNm}). 지역만 친 검색어({@code 노원})면 {@code null}
 * @param sidoCd  시도 코드. 지역을 못 풀면 {@code null}
 * @param sgguCd  시군구 코드. 시도까지만 풀렸으면({@code 서울 피부과}) {@code null}
 */
public record HospitalQuery(String name, String sidoCd, String sgguCd) {

    /** 지역 없이 이름으로만 찾는다. 해석기가 못 풀었을 때다. */
    public static HospitalQuery byName(String name) {
        return new HospitalQuery(name, null, null);
    }

    /**
     * 캐시 키. 검색어 문자열이 아니라 <b>푼 결과</b>로 잡는다 — {@code 노원 피부과} 와
     * {@code 노원구 피부과} 는 같은 질의라 상류를 두 번 부를 이유가 없다.
     */
    public String cacheKey() {
        return sidoCd + "|" + sgguCd + "|" + name;
    }
}
