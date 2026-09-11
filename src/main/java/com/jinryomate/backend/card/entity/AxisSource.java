package com.jinryomate.backend.card.entity;

/**
 * 축 값이 어디서 왔는지.
 *
 * <p><b>의사가 "환자가 말한 그대로"와 "나중에 고친 값"을 구별할 수 있어야 한다.</b>
 * 그게 이 값의 존재 이유다.
 *
 * <p>{@code evidence} 가 비지 않는 이유 — "근거 없는 값은 만들지 않는다"는 <b>AI 가</b>
 * 근거 없이 값을 만들지 않는다는 뜻이다. 환자가 직접 쓴 값은 그 자체가 근거라,
 * {@code "[환자 수정] 3일 전부터"} 처럼 남긴다.
 */
public enum AxisSource {

    /** 환자 발화에서 AI 가 추출했다. 기본값. */
    AI_EXTRACTION,

    /** 칩·폼으로 골랐다. {@code evidence} 는 {@code "[선택] 5점"} 형태다. */
    SELECTION,

    /**
     * 환자가 S3 편집 화면에서 고쳤다. {@code evidence} 는 {@code "[환자 수정] …"} 형태다.
     *
     * <p><b>이 값은 우리가 채운다.</b> 편집은 우리 화면에서 일어나 AI 를 거치지 않는다.
     */
    PATIENT_EDIT
}
