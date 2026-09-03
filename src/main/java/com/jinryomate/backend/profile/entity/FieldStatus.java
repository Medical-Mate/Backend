package com.jinryomate.backend.profile.entity;

/**
 * 프로필·카드 필드의 세 가지 상태.
 *
 * <p>{@link #NONE}과 {@link #UNKNOWN}을 {@code null} 하나로 뭉개면 안 된다.
 * 의사용 카드에 "알레르기: 본인 확인 못 함"을 찍으려면 둘을 구분해야 한다.
 * 와이어프레임에 이미 있는 문구다.
 */
public enum FieldStatus {

    /** 값이 있다. */
    KNOWN,

    /** 환자가 "없어요"라고 답했다. */
    NONE,

    /** 환자가 "잘 모르겠어요"라고 답했다. */
    UNKNOWN
}
