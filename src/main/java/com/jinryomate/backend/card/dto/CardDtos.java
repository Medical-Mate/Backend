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

    /**
     * 기록 탭의 목록 항목 (화면 1j).
     *
     * <p>목록에는 본문을 담지 않는다. 증상·복용약이 든 카드를 목록마다 통째로 실어 나를
     * 이유가 없다. 상세는 {@code GET /api/cards/{id}} 로 본다.
     *
     * @param visited    진료를 마쳤는지. 카드의 {@code DRAFT}/{@code CONFIRMED} 와는 다른 축이라
     *                   컬럼을 두지 않고 <b>진료 기록이 붙었는지</b>로 판단한다.
     *                   같은 사실을 두 곳에 적으면 어긋난다
     * @param clinicName 진료 기록의 병원명. 병원명은 선택 입력이라
     *                   <b>진료를 마쳤어도 비어 있을 수 있다</b>. {@code visited} 의 근거로 쓰지 않는다
     */
    public record CardSummary(
            Long cardId,
            String title,
            CardStatus status,
            boolean visited,
            String clinicName,
            Instant createdAt
    ) {
        public static CardSummary of(BriefingCard c, boolean visited, String clinicName) {
            return new CardSummary(
                    c.getId(),
                    c.getTitle(),
                    c.getStatus(),
                    visited,
                    clinicName,
                    c.getCreatedAt());
        }
    }

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
