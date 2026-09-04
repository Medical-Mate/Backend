package com.jinryomate.backend.card.entity;

/**
 * 카드 상태.
 *
 * <p>{@link #CONFIRMED} 이후의 변경은 이 카드를 고치지 않고 새 버전을 만든다.
 * 의사가 본 카드가 뒤바뀌면 안 되기 때문이다.
 */
public enum CardStatus {

    /** 환자가 확인·수정하는 중. */
    DRAFT,

    /** 확정됨. 더 이상 수정하지 않는다. */
    CONFIRMED
}
