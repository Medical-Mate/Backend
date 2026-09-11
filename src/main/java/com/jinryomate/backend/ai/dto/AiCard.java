package com.jinryomate.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * AI 가 턴 응답에 실어 주는 진료 전 카드.
 *
 * <p><b>카드는 매 턴 온다.</b> 카드만 따로 만드는 경로는 계약에 없다 — 축 값이 쌓인
 * {@code state} 를 직렬화한 것이라, 같은 {@code state} 면 같은 카드가 바이트까지 같게 나온다.
 *
 * <p>모르는 필드는 무시한다({@link JsonIgnoreProperties}). AI 가 {@code title} ·
 * {@code question_candidates} · 축의 {@code source} 를 넣기로 했는데 아직 안 내려온다.
 * 올라오면 여기에 필드만 더하면 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCard(
        @JsonProperty("card_type") String cardType,

        /** 환자가 말한 그대로. <b>줄이지 않는다</b> — 줄이는 순간 환자 말이 아니다. */
        @JsonProperty("chief_complaint") String chiefComplaint,

        /** AI 가 부위 + 기간을 결정론으로 조합해 만든다. 아직 안 내려온다. */
        String title,

        /** 축 이름 → 값. 8축이지만 개수를 고정하지 않는다. */
        Map<String, Axis> axes,

        @JsonProperty("red_flags") List<String> redFlags,

        /** 축 밖으로 새는 환자 말. 예: {@code "타이레놀 먹었어요"}. */
        @JsonProperty("patient_notes") List<String> patientNotes,

        @JsonProperty("minimally_complete") Boolean minimallyComplete,

        /** 8축 중 몇 개가 찼는지. {@code 0.375} 형태. */
        Double completeness,

        /**
         * 환자가 짚은 <b>부위 노드의 속성</b>이다. 증상에서 고른 것이 아니라 감별진단이 아니다.
         *
         * <p>부위 34곳 중 14곳은 진료과가 없어 통째로 {@code null} 이 온다.
         */
        @JsonProperty("department_guidance") DepartmentGuidance departmentGuidance,

        Provenance provenance
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Axis(
            String status,
            String value,
            List<String> evidence,
            /** {@code ai_extraction} · {@code selection}. 아직 안 내려온다. */
            String source
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepartmentGuidance(
            /** 배열이다. <b>하나로 좁히는 순간 그게 추천이 된다.</b> */
            List<String> departments,
            /** {@code "팀 결정 2026-09-04, 의료인 자문 확인 전"}. 화면에 함께 보여야 한다. */
            String source,
            String note
    ) {}

    /**
     * 무엇이 이 카드를 만들었는지.
     *
     * <p>프롬프트 판과 모델을 따로 받는다. 프롬프트가 같아도 모델이 바뀌면 결과가 달라지므로
     * 하나로는 못 짚는다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provenance(
            @JsonProperty("prompt_version") String promptVersion,
            @JsonProperty("model_id") String modelId,
            @JsonProperty("ontology_snapshot") String ontologySnapshot
    ) {}
}
