package com.jinryomate.backend.card.dto;

import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 브리핑 카드의 요청·응답. */
public final class CardDtos {

    private CardDtos() {}

    // ---------- 공통 ----------

    /**
     * 카드 축 하나.
     *
     * @param status   <b>5값이다</b>({@code not_asked} 포함). 프로필 필드의 3값과 다르다
     * @param evidence 환자 발화 원문. 의사가 "정말 저렇게 말했나"를 확인하는 근거다
     * @param source   {@code patient_edit} 이면 환자가 나중에 고친 값이다
     */
    public record Axis(
            String axis,
            AxisStatus status,
            String value,
            List<String> evidence,
            AxisSource source
    ) {
        public static Axis from(CardAxis a) {
            return new Axis(a.getAxis(), a.getStatus(), a.getValue(),
                    List.copyOf(a.getEvidence()), a.getSource());
        }
    }

    /**
     * 기록 탭의 목록 항목 (화면 1j).
     *
     * <p>목록에는 본문을 담지 않는다. 증상이 든 카드를 목록마다 통째로 실어 나를 이유가 없다.
     * 상세는 {@code GET /api/cards/{id}} 로 본다.
     *
     * @param visited    진료를 마쳤는지. 카드의 {@code DRAFT}/{@code CONFIRMED} 와는 다른 축이라
     *                   컬럼을 두지 않고 <b>진료 기록이 붙었는지</b>로 판단한다
     * @param clinicName 진료 기록의 병원명. 선택 입력이라 <b>진료를 마쳤어도 비어 있을 수 있다</b>
     */
    public record CardSummary(
            Long cardId,
            String title,
            String chiefComplaint,
            CardStatus status,
            boolean visited,
            String clinicName,
            Instant createdAt
    ) {
        public static CardSummary of(BriefingCard c, boolean visited, String clinicName) {
            return new CardSummary(
                    c.getId(),
                    c.displayTitle(),
                    c.getChiefComplaint(),
                    c.getStatus(),
                    visited,
                    clinicName,
                    c.getCreatedAt());
        }
    }

    // ---------- 요청 ----------

    /**
     * 환자가 S3 화면에서 카드를 고칠 때. 보낸 것만 바뀐다.
     *
     * <p><b>제목은 고칠 수 없다.</b> AI 가 부위 + 기간을 결정론으로 조합해 만드는 값이라
     * 환자가 손대면 그 규칙이 깨진다.
     *
     * <p><b>진료과도 고칠 수 없다.</b> 환자가 짚은 부위 노드의 속성이고, 우리도 AI 도
     * 증상에서 과를 고르지 않기로 했다.
     */
    public record UpdateCardRequest(
            @Size(max = 500, message = "500자 이내로 입력해주세요.")
            String chiefComplaint,

            @Valid
            @Size(max = 12, message = "한 번에 고칠 수 있는 항목은 12개까지입니다.")
            List<AxisEdit> axes,

            @Size(max = 3, message = "질문은 최대 3개입니다.")
            List<@Size(max = 40, message = "질문은 40자 이내입니다.") String> questions,

            @Size(max = 10, message = "메모는 최대 10개입니다.")
            List<@Size(max = 200) String> patientNotes
    ) {}

    /**
     * 축 하나를 고친다.
     *
     * <p>{@code value} 를 비우면 "모르겠다"({@link AxisStatus#UNKNOWN})가 된다.
     * 지우는 것과 모른다고 하는 것을 구별하지 않는 이유 — 카드에서 값을 지우는 행동은
     * 의사에게 "이 항목은 확인 못 했다"로 보여야 한다.
     */
    public record AxisEdit(
            @NotBlank(message = "항목 이름이 필요합니다.")
            @Size(max = 32)
            String axis,

            @Size(max = 80, message = "80자 이내로 입력해주세요.")
            String value
    ) {}

    // ---------- 응답 ----------

    /**
     * @param axes           축 이름 → 축. 8축이 전부 들어 있고, 아직 안 물어본 축은
     *                       {@code not_asked} 로 자리만 있다
     * @param rejectedFields 검증에 걸려 {@code unknown} 으로 저장된 것.
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
            String chiefComplaint,
            Map<String, Axis> axes,
            List<String> redFlags,
            List<String> patientNotes,
            List<String> questions,
            DepartmentGuidance departmentGuidance,
            Double completeness,
            Boolean minimallyComplete,

            Meta meta,
            List<String> rejectedFields,
            Instant createdAt,
            Instant confirmedAt
    ) {
        /**
         * @param allergies 시안의 카드는 이 값을 경고 면 맨 위에 올린다.
         *                  {@code status} 가 {@code NONE}("없어요")인지 {@code UNKNOWN}
         *                  ("본인 확인 못 함")인지가 의사에게 전혀 다른 말이다
         */
        public record Patient(String name, Integer age, Sex sex,
                              Allergies allergies,
                              ListField medications,
                              ListField conditions) {}

        public record Allergies(FieldStatus status, String text) {}

        /**
         * 복용약·기저질환. 프로필 응답의 {@code ListFieldResponse} 와 같은 모양이라
         * 앱이 파싱을 한 벌로 쓴다.
         *
         * <p><b>프로필이 아니라 카드를 만든 시점의 사본이다.</b> 나중에 약이 바뀌어도
         * 이미 만들어진 카드는 그대로다.
         */
        public record ListField(FieldStatus status, List<String> items) {}

        /**
         * 진료과 안내.
         *
         * <p><b>배열이다. 하나로 좁히지 않는다</b> — 좁히는 순간 그게 추천이 되고, 증상에서
         * 과를 고르는 것은 감별진단이다. 순서에도 의미가 없다.
         *
         * <p>부위 34곳 중 14곳은 진료과가 없어 <b>빈 목록</b>이다. 그때는 화면에서 줄을 숨긴다.
         *
         * @param source {@code "팀 결정 …, 의료인 자문 확인 전"}. <b>화면에 함께 보여야 한다</b> —
         *               인용이 아니라는 것을 숨기지 않는 것이 이 값의 목적이다
         */
        public record DepartmentGuidance(List<String> departments, String source) {}

        /** 무엇이 이 카드를 만들었는지. 프롬프트가 같아도 모델이 바뀌면 결과가 달라진다. */
        public record Meta(String promptVersion, String modelId, String ontologySnapshot, String requestId) {}

        public static CardResponse from(BriefingCard c) {
            return from(c, List.of());
        }

        public static CardResponse from(BriefingCard c, List<String> rejectedFields) {
            Map<String, Axis> axes = new java.util.LinkedHashMap<>();
            c.axesByName().forEach((name, a) -> axes.put(name, Axis.from(a)));

            return new CardResponse(
                    c.getId(),
                    c.getStatus(),
                    c.getVersion(),
                    c.getParentCard() == null ? null : c.getParentCard().getId(),
                    c.getSession().getId(),
                    new Patient(c.getPatientName(), c.getPatientAge(), c.getPatientSex(),
                            new Allergies(c.getAllergiesStatus(), c.getAllergiesText()),
                            new ListField(c.getMedicationsStatus(), List.copyOf(c.getMedications())),
                            new ListField(c.getConditionsStatus(), List.copyOf(c.getConditions()))),
                    c.getTitle(),
                    c.getChiefComplaint(),
                    axes,
                    List.copyOf(c.getRedFlags()),
                    List.copyOf(c.getPatientNotes()),
                    List.copyOf(c.getQuestions()),
                    new DepartmentGuidance(
                            List.copyOf(c.getDepartmentGuidance()), c.getDepartmentGuidanceSource()),
                    c.getCompleteness(),
                    c.getMinimallyComplete(),
                    new Meta(c.getPromptVersion(), c.getModelId(),
                            c.getOntologySnapshot(), c.getAiRequestId()),
                    List.copyOf(rejectedFields),
                    c.getCreatedAt(),
                    c.getConfirmedAt());
        }
    }
}
