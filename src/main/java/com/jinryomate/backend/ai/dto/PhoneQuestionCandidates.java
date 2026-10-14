package com.jinryomate.backend.ai.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 폰이 만든 질문 후보. AI 계약의 {@code phone_question_candidates} + {@code candidates_meta}.
 *
 * <p>AI 는 이걸 받으면 <b>LLM 을 부르지 않고</b> 검증기로 거르고 모자라면 템플릿으로
 * 채운다(AI#137). 빈 목록도 된다 — 폰이 하나도 못 만들었다는 뜻이다.
 *
 * @param items 후보. 비어 있어도 된다
 * @param meta  어떤 폰 모델·프롬프트가 만들었나. <b>열어보지 않고 그대로 넘긴다.</b> 없으면 {@code null}
 */
public record PhoneQuestionCandidates(List<Item> items, JsonNode meta) {

    /**
     * @param source 어느 칸에서 왔나(폰 모델이 단 것). 틀려도 AI 가 다시 본다. 없으면 {@code null}
     */
    public record Item(String text, String source) {}
}
