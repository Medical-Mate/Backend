package com.jinryomate.backend.ai.dto;

import com.jinryomate.backend.card.entity.CardAxis;
import java.util.List;
import java.util.Map;

/**
 * 진료 후 메모를 축으로 나눈 결과. AI {@code POST /v1/postvisit/memo} 의 응답이다.
 *
 * <p>네 축이 온다 — {@code findings}(소견) · {@code tests}(검사) ·
 * {@code medication_instructions}(약·생활 지시) · {@code follow_up}(재방문). 모양은 브리핑 카드의
 * 축과 같아서 앱이 그리는 코드를 나눠 쓴다.
 *
 * <p><b>{@code labels} 를 그대로 돌려주는 것이 핵심이다.</b> 문장 번호 → 축 이름 맵인데,
 * 이걸 다시 보내면 AI 가 모델을 부르지 않고 재조립만 한다. 환자가 1q-1-E 에서 줄을 옮길
 * 때마다 Bedrock 을 부르면 한 번 정리에 열 번이 나간다.
 *
 * @param axes         축 이름 → 축. 순서는 AI 가 준 그대로
 * @param sentences    메모를 문장으로 쪼갠 것. {@code labels} 의 키가 이 배열의 인덱스다
 * @param labels       문장 인덱스(문자열) → 축 이름. 수정 저장 때 되돌려 보낸다
 * @param patientNotes 어느 축에도 안 들어간 문장. <b>버리지 않는다</b>
 * @param followUp     재방문 시점. 날짜 하나가 아니라 원문·날짜·"전후" 여부를 함께 담는다
 * @param splitVersion 무엇이 이 문장들을 나눴는지({@code "split-v2"}). <b>{@code labels} 를
 *                     되보낼 때 같이 보낸다</b> — 그 사이 규칙이 바뀌었으면 409 가 온다
 * @param source       누가 라벨을 붙였나. {@code server} 모델 · {@code client} 환자가 고친 것을
 *                     되보냄 · {@code none} 문장만 나눔. <b>화면에 안 쓴다</b> — 감사 기록을
 *                     나중에 거를 때만 본다
 * @param audit        AI 가 무엇을 보고 무엇을 뱉었는지. <b>통째로 문자열이고 열어보지 않는다.</b>
 *                     AI 회귀 eval 재료로 저장만 한다(Medical-Mate/AI#113). 계약이 안 주면 null
 */
public record MemoClassification(
        Map<String, CardAxis> axes,
        List<String> sentences,
        Map<String, String> labels,
        List<String> patientNotes,
        FollowUp followUp,
        String promptVersion,
        String modelId,
        String splitVersion,
        String source,
        String audit
) {}
