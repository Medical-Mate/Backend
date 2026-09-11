package com.jinryomate.backend.intake.entity;

/**
 * 추출을 어디서 돌리는지.
 *
 * <p><b>우리가 고르지 않는다.</b> 앱이 세션을 시작할 때 정해 보내고, 우리는 AI 에 그대로
 * 넘긴다. 폰에 모델을 들고 있는지는 기기 사정이라 서버가 알 수 없다.
 */
public enum AiProfile {

    /** 서버(Bedrock)가 추출한다. 기본값. */
    SERVER,

    /**
     * 폰 안에서 추출한다.
     *
     * <p>턴마다 앱이 {@code extraction} 을 만들어 보내고 AI 는 LLM 을 부르지 않는다.
     * 문답도 달라진다 — 첫 자유 발화 없이 첫 축 질문부터 시작한다.
     *
     * <p><b>발화 원문은 그래도 온다.</b> AI 가 근거를 검증해야 하기 때문이다. 외부 LLM
     * 업체에 안 가는 것이지 우리에게 안 오는 것이 아니다.
     */
    ONDEVICE;

    /** AI 계약이 쓰는 표기. {@code server} · {@code ondevice}. */
    public String toContract() {
        return name().toLowerCase();
    }
}
