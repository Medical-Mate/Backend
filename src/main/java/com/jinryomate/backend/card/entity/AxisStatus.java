package com.jinryomate.backend.card.entity;

/**
 * 카드 축 하나의 상태. <b>AI 계약이 정한다</b>({@code schema/card.py} 의 {@code FieldStatus}).
 *
 * <p>프로필 필드의 {@link com.jinryomate.backend.profile.entity.FieldStatus}(3값)와 다르다.
 * 섞으면 안 된다 — 프로필은 우리가 정한 값 집합이고, 여기는 AI 가 주는 값 집합이다.
 *
 * <p><b>{@link #NOT_ASKED} 가 기본값이다.</b> 진료 전 카드는 8축을 전부 {@code not_asked} 로
 * 만들어 두고 채워 나간다. 1턴째 카드는 대부분의 축이 이 값이라, 3값으로 검증하면 거의
 * 모든 카드가 떨어진다.
 */
public enum AxisStatus {

    /** 아직 묻지 않았다. 기본값. */
    NOT_ASKED,

    /** 환자가 답했고 값이 있다. */
    FILLED,

    /** 환자가 "모르겠다"고 했다. */
    UNKNOWN,

    /** 환자가 답하지 않고 넘겼다. */
    SKIPPED,

    /**
     * 발화가 축을 건드렸지만 값을 확정할 수 없다.
     *
     * <p>최종 카드에도 남을 수 있다. AI 는 1회만 되묻고, 되물어도 확정이 안 되면
     * 그 상태로 닫는다.
     */
    AMBIGUOUS
}
