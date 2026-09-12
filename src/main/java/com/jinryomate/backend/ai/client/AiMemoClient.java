package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.MemoClassification;
import java.time.LocalDate;
import java.util.Map;

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
     * <p><b>{@code labels} 가 있으면 모델을 부르지 않는다.</b> 앞선 호출이 돌려준 라벨을 그대로
     * 넘기면 AI 가 재조립만 한다. 환자가 줄을 옮길 때마다 Bedrock 을 부르지 않기 위한
     * 자리이고, 비용은 우리 크레딧에서 나간다.
     *
     * @param memo       환자가 적은 원문. 2000자까지
     * @param visitedOn  진료일. 재방문 날짜를 "2주 뒤"에서 계산하는 기준이 된다. 없으면 {@code null}
     * @param clinicName 병원 이름. 없으면 {@code null}
     * @param labels     문장 인덱스 → 축 이름. 있으면 이대로 조립한다. 처음 부를 때는 {@code null}
     */
    MemoClassification classify(String memo, LocalDate visitedOn, String clinicName,
                                Map<String, String> labels);
}
