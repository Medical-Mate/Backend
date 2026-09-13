package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.ai.dto.MemoRequest;

/**
 * 진료 후 메모를 항목으로 나눠주는 AI 서비스 호출. 화면 {@code 1p} 의 "AI로 정리하기".
 *
 * <p>문답 세션({@link AiTurnClient})과 다른 자리다. 저쪽은 진료 <b>전</b> 대화라
 * {@code state} 를 이어가지만, 여기는 메모 한 덩이를 넣고 나눈 결과를 받는 한 번의 호출이다.
 *
 * <p><b>나누는 것과 저장하는 것은 다른 호출이다.</b> 환자가 나눈 결과를 1q-1-E 에서 고친 뒤
 * 저장하기 때문이다.
 */
public interface AiMemoClient {

    /**
     * 메모를 네 축으로 나눈다.
     *
     * <p><b>모델을 안 부르는 경로가 둘 있다.</b> 둘 다 비용이 0 이고, 그 비용은 우리
     * 크레딧에서 나가므로 켜는 시점을 우리가 쥔다.
     *
     * <ul>
     *   <li>{@code classify=false} · 라벨 없음 — <b>문장만</b> 나눠 돌려준다. 폰이 분류할 때 1단계
     *   <li>라벨 있음 — 그 라벨대로 <b>조립만</b> 한다. 1q-1-E 수정과 온디바이스 3단계가 같은 경로다
     * </ul>
     */
    MemoClassification classify(MemoRequest request);
}
