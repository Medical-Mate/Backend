package com.jinryomate.backend.intake.entity;

/**
 * 부위의 좌우.
 *
 * <p><b>부위 코드에 박지 않는다.</b> {@code SUR:051}(어깨) 하나가 좌우를 다 덮고 구분은
 * 이 값이 한다. 코드에 박으면 노드가 두 배로 늘고, 좌우가 없는 부위(머리·배)와 규칙이 갈린다.
 *
 * <p><b>{@code laterality} 가 {@code left_right} 인 부위에만 붙일 수 있다.</b> 34곳 중
 * 21곳이 그렇고, 나머지 13곳에 보내면 거부한다 — "머리에는 좌우가 없다".
 *
 * <p>라벨 앞에 붙는 말({@code 오른쪽})은 AI 가 만든다. 우리가 문구를 만들지 않는다.
 */
public enum Side {
    LEFT,
    RIGHT,
    BOTH;

    /** AI 계약이 쓰는 표기. {@code left} · {@code right} · {@code both}. */
    public String toContract() {
        return name().toLowerCase();
    }
}
