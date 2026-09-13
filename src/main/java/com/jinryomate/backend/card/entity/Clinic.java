package com.jinryomate.backend.card.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 진료받을 병원. 시안 {@code 1m-B} 에서 고르고 {@code 1e-1} 하단에 찍힌다.
 *
 * <p><b>이름과 주소뿐이다.</b> 주소가 없으면 같은 이름의 다른 지점을 구별할 수 없고,
 * 그 둘 말고는 화면이 쓰는 값이 없다. 병원 검색({@code GET /api/hospitals})이 내주는
 * 것과 같은 모양이라 고른 결과를 그대로 옮길 수 있다.
 *
 * <p><b>진료과를 따로 두지 않는다.</b> 심평원 기관명에 이미 들어 있다 —
 * {@code The서울아산내과의원} · {@code 배곧서울아산이비인후과의원}.
 *
 * <p><b>스냅샷이다.</b> 요양기호만 저장하고 매번 심평원을 부를 수도 있지만, 상류가
 * 1.6초이고 할당량이 있다. 카드는 환자 이름·알레르기·복용약도 만든 시점 값으로
 * 박아두고 있어서, 병원이 이름을 바꾸거나 폐업해도 "그때 가려던 병원"이 남아야 한다.
 */
public record Clinic(String name, String address) {

    /** 아직 안 골랐을 때. 화면은 "병원 미정" 으로 찍는다. */
    public static final Clinic NONE = new Clinic(null, null);

    /** 응답에 나가지 않는다. 우리 안에서만 쓰는 판단이다. */
    @JsonIgnore
    public boolean isBlank() {
        return (name == null || name.isBlank()) && (address == null || address.isBlank());
    }
}
