package com.jinryomate.backend.card.dto;

import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.card.entity.Clinic;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

            /**
             * 진료받을 병원. 화면 {@code 1r-4-B} 의 {@code 09.04 작성 · 서울OO병원 내과}.
             *
             * <p><b>위 {@code clinicName} 과 다른 값입니다.</b> 저건 진료를 <b>받은</b> 병원
             * (진료 기록에서 옵니다), 이건 <b>받을</b> 병원(카드가 들고 있는 값)입니다.
             * 안 골랐으면 안쪽이 비어 있습니다 — 화면은 "병원 미정".
             */
            Clinic clinic,

            /**
             * 언제 쓴 카드인지. 화면의 {@code 09.04 작성}.
             *
             * <p><b>고쳐도 안 바뀝니다.</b> 확정한 카드를 고치면 서버에 새 버전 행이 생기지만
             * 그건 저희 사정이고, 환자에게는 같은 카드를 고친 것입니다. 맨 처음 만든 시각을
             * 그대로 냅니다.
             */
            Instant createdAt
    ) {
        /**
         * @param cardId  <b>가장 최신 버전의 id</b> 입니다. 눌러 들어가면 최신 카드가 열립니다
         * @param written 체인 첫 행의 작성 시각. 목록의 {@code createdAt} 이 이 값입니다
         */
        public static CardSummary of(BriefingCard c, boolean visited, String clinicName,
                                     Instant written) {
            return new CardSummary(
                    c.getId(),
                    c.displayTitle(),
                    c.getChiefComplaint(),
                    c.getStatus(),
                    visited,
                    clinicName,
                    c.clinic(),
                    written != null ? written : c.getCreatedAt());
        }
    }

    // ---------- 요청 ----------

    /**
     * 진료받을 병원. 병원 검색({@code GET /api/hospitals})이 준 항목을 그대로 옮기면 됩니다.
     *
     * <p>진료과는 따로 받지 않습니다 — 심평원 기관명에 이미 들어 있습니다
     * ({@code The서울아산내과의원}).
     */
    public record ClinicRequest(
            @NotBlank(message = "병원명이 필요합니다.")
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String name,

            @Size(max = 200, message = "주소는 200자 이내입니다.")
            String address
    ) {}

    /**
     * 카드를 만들 때 함께 보내는 것. 화면 {@code 1m-B}.
     *
     * <p><b>본문 전체가 선택입니다.</b> "아직 정하지 않았다면 건너뛰어도 돼요" 가 그 화면에
     * 있어서, 안 보내면 병원 없이 만들어집니다.
     */
    public record GenerateCardRequest(
            @Valid
            ClinicRequest clinic
    ) {}

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
            List<@Size(max = 200) String> patientNotes,

            /** 진료받을 병원. 화면 {@code 1e-1} 의 "변경". 안 보내면 안 바뀝니다. */
            @Valid
            ClinicRequest clinic
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

            /**
             * 진료받을 병원. 시안 {@code 1e-1} 하단의 "진료받을 병원".
             *
             * <p><b>카드 자신의 값입니다.</b> {@code 1m-B} 에서 고르고 {@code 1e-1} 의
             * "변경"으로 바꿉니다. 안 골랐으면 안쪽이 비어 있습니다("병원 미정").
             *
             * <p>아래 {@code appointment} 의 병원과 다를 수 있습니다 — 이건 "이 카드를 어디로
             * 가져갈 것인가", 저건 "그 일정이 어느 병원인가" 입니다.
             */
            Clinic clinic,

            /**
             * 연결된 진료 예정 일정. <b>병원은 {@code clinic} 을 보세요.</b>
             *
             * <p>여기는 일정 자체(시각 · 이동에 쓸 id)를 그리는 데 씁니다.
             * 일정을 안 잡았으면 {@code null} 입니다.
             */
            Appointment appointment,

            /**
             * 진료를 <b>받은</b> 병원. 아직 진료 전이면 {@code null} 입니다.
             *
             * <p><b>기록이 여럿이면 가장 최근 한 건입니다.</b> 재방문이 쌓여도 이 자리가
             * 답하는 것은 "지금 이 카드로 마지막에 어디를 다녀왔나" 입니다. 전부 보려면
             * 카드별 진료 목록({@code GET /api/cards/…/visits})을 쓰세요.
             *
             * <p>{@code appointment} 와 다를 수 있습니다 — 예약은 A 병원에 잡아 두고
             * 실제로는 B 병원에 갈 수 있습니다.
             */
            Visit visit,

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

        /**
         * 카드에 연결된 진료 예정 일정. 진료를 <b>받을</b> 병원이다.
         *
         * @param scheduledTime 시각을 아직 안 정했으면 {@code null} 이다. 날짜만 잡아 두는
         *                      상태가 화면 {@code 1r-2-A} 에 있다
         */
        public record Appointment(Long appointmentId, String clinicName,
                                  String department, LocalDate scheduledOn,
                                  LocalTime scheduledTime) {}

        /** 카드에 달린 진료 후 기록. 진료를 <b>받은</b> 병원이다. */
        public record Visit(Long visitId, String clinicName, LocalDate visitedOn) {}

        public static CardResponse from(BriefingCard c, CardLinks links) {
            return from(c, List.of(), links);
        }

        public static CardResponse from(BriefingCard c, List<String> rejectedFields, CardLinks links) {
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
                    c.clinic(),
                    links.appointment(),
                    links.visit(),
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
