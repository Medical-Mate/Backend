package com.jinryomate.backend.card.entity;

/**
 * 카드 상태.
 *
 * <p>{@link #CONFIRMED} 이후의 변경은 이 카드를 고치지 않고 새 버전을 만든다.
 * 의사가 본 카드가 뒤바뀌면 안 되기 때문이다.
 *
 * <p><b>{@link #DRAFT} 는 더 이상 생기지 않는다.</b> 앱이 "저장하기" 단계를 없애면서
 * 카드가 확정된 채로 태어난다(#181). 남아 있던 초안은 V28 이 확정으로 옮겼다. 값을
 * 지우지 않는 것은 응답의 {@code status} 와 DB 제약이 이 이름을 알고 있어서다.
 */
public enum CardStatus {

    /** 환자가 확인·수정하는 중. 지금은 만드는 경로가 없다. */
    DRAFT,

    /** 확정됨. 더 이상 수정하지 않는다. */
    CONFIRMED
}
