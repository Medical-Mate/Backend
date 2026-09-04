package com.jinryomate.backend.card.dto;

import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.card.entity.Department;
import com.jinryomate.backend.card.entity.Medication;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 브리핑 카드의 요청·응답. */
public final class CardDtos {

    private CardDtos() {}

    // ---------- 공통 ----------

    public record TextField(FieldStatus status, String text) {}

    // ---------- 요청 ----------

    /**
     * 환자가 S3 화면에서 카드를 고칠 때. 보낸 필드만 바뀐다.
     *
     * <p>여기서 들어온 값도 서버 검증을 거친다. 제목에 진단명이 들어가면
     * AI가 준 것이든 환자가 넣은 것이든 똑같이 막는다.
     */
    public record UpdateCardRequest(
            @Size(max = 40, message = "제목이 너무 깁니다.")
            String title,

            @Valid TextFieldRequest onset,
            @Valid TextFieldRequest pattern,
            @Valid TextFieldRequest allergies,

            @Size(max = 5, message = "질문은 최대 5개까지 보낼 수 있습니다.")
            List<@Size(max = 100) String> questions,

            Department suggestedDepartment
    ) {}

    public record TextFieldRequest(
            @NotNull(message = "상태가 필요합니다.")
            FieldStatus status,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String text
    ) {}

    // ---------- 응답 ----------

    /**
     * @param rejectedFields 검증에 걸려 {@code UNKNOWN}으로 저장된 필드.
     *                       앱이 "이 항목은 직접 채워주세요"라고 안내하는 데 쓴다
     */
    public record CardResponse(
            Long cardId,
            CardStatus status,
            int version,
            Long parentCardId,
            Long sessionId,

            Patient patient,

            String title,
            TextField onset,
            TextField pattern,
            Site site,
            Medications medications,
            TextField allergies,
            List<String> questions,
            Department suggestedDepartment,
            Map<String, List<Integer>> evidence,

            Meta meta,
            List<String> rejectedFields,
            Instant createdAt,
            Instant confirmedAt
    ) {
        public record Patient(String name, Integer age, Sex sex) {}

        public record Site(FieldStatus status, String text, List<String> codes) {}

        public record Medications(FieldStatus status, List<Medication> items) {}

        /** 어떤 AI 파이프라인이 만들었는지. 장애 시 AI 로그와 잇는 값이다. */
        public record Meta(String pipelineVersion, String requestId) {}

        public static CardResponse from(BriefingCard c) {
            return from(c, List.of());
        }

        public static CardResponse from(BriefingCard c, List<String> rejectedFields) {
            return new CardResponse(
                    c.getId(),
                    c.getStatus(),
                    c.getVersion(),
                    c.getParentCard() == null ? null : c.getParentCard().getId(),
                    c.getSession().getId(),
                    new Patient(c.getPatientName(), c.getPatientAge(), c.getPatientSex()),
                    c.getTitle(),
                    new TextField(c.getOnsetStatus(), c.getOnsetText()),
                    new TextField(c.getPatternStatus(), c.getPatternText()),
                    new Site(c.getSiteStatus(), c.getSiteText(), List.copyOf(c.getSiteCodes())),
                    new Medications(c.getMedicationsStatus(), List.copyOf(c.getMedications())),
                    new TextField(c.getAllergiesStatus(), c.getAllergiesText()),
                    List.copyOf(c.getQuestions()),
                    c.getSuggestedDepartment(),
                    Map.copyOf(c.getEvidence()),
                    new Meta(c.getPipelineVersion(), c.getAiRequestId()),
                    List.copyOf(rejectedFields),
                    c.getCreatedAt(),
                    c.getConfirmedAt());
        }
    }
}
