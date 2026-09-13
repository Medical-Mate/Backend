package com.jinryomate.backend.visit.dto;

import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.ai.dto.LabelsMeta;
import com.jinryomate.backend.card.dto.CardDtos.Axis;
import com.jinryomate.backend.visit.entity.VisitRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 진료 후 기록의 요청·응답. */
public final class VisitDtos {

    private VisitDtos() {}

    // ---------- 요청 ----------

    /**
     * 재방문 시점. 분류 응답의 {@code followUp} 을 그대로 돌려보내면 됩니다.
     *
     * <p>{@code approximate} 가 {@code true} 면 화면에 "전후" 를 붙입니다 —
     * "2주 뒤" 는 날짜가 아니라 범위입니다.
     */
    public record FollowUpRequest(
            LocalDate date,

            @Size(max = 60, message = "60자 이내로 입력해주세요.")
            String text,

            boolean approximate
    ) {}

    /**
     * 저장할 항목 한 줄. 화면 {@code 1q-1-E} 가 보내는 것.
     *
     * <p>{@code status} 는 받지 않는다. 환자가 값을 적었으면 {@code FILLED}, 비웠으면
     * {@code UNKNOWN} 이고, 그건 값을 보면 안다. 앱이 잘못 보낼 자리를 만들지 않는다.
     *
     * <p>{@code source} 도 받지 않는다. <b>출처는 서버가 정한다</b> — 앱이 "AI 가 뽑았다"고
     * 주장할 수 있으면 의사 화면의 출처 표시가 의미를 잃는다.
     */
    public record VisitAxisRequest(
            @NotBlank(message = "항목 이름이 필요합니다.")
            @Size(max = 32)
            String axis,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String value
    ) {}

    /**
     * 진료 직후 기록.
     *
     * <p>모든 항목이 선택이다. 병원을 막 나온 환자에게 필수 입력을 강요하면 아무것도 안 남는다.
     * 원문({@code rawNote})만 적어도 저장된다.
     *
     * @param axes         소견 · 검사 · 약 · 재방문. <b>칸이 고정이 아니다</b> — AI 가 나눈
     *                     항목을 그대로 보내면 된다
     * @param followUp     재방문 시점. 분류 응답의 {@code followUp} 을 그대로 옮기면 된다.
     *                     캘린더 일정은 여기서 만들지 않는다 —
     *                     앱이 {@code POST /api/me/appointments} 를 따로 부른다
     * @param patientNotes 어느 항목에도 안 들어간 문장
     */
    public record CreateVisitRequest(
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            LocalDate visitedOn,

            @Valid
            @Size(max = 12, message = "한 번에 보낼 수 있는 항목은 12개까지입니다.")
            List<VisitAxisRequest> axes,

            FollowUpRequest followUp,

            @Size(max = 20, message = "메모는 최대 20개입니다.")
            List<@Size(max = 500) String> patientNotes,

            @Size(max = 2000, message = "2000자 이내로 입력해주세요.")
            String rawNote,

            @Valid
            LabelsMetaRequest extractedBy
    ) {}

    /**
     * 기록 수정. 화면 {@code 1q-1-E} 의 "전체 수정".
     *
     * <p><b>보낸 항목만 바뀐다.</b> {@code null} 인 필드는 건드리지 않는다. 다만
     * {@code axes} 는 <b>보내면 통째로 갈아끼운다</b> — 환자가 줄을 지우면 그 항목이 사라져야
     * 하는데, 병합이면 지운 줄이 남는다.
     */
    public record UpdateVisitRequest(
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            LocalDate visitedOn,

            @Valid
            @Size(max = 12, message = "한 번에 보낼 수 있는 항목은 12개까지입니다.")
            List<VisitAxisRequest> axes,

            FollowUpRequest followUp,

            @Size(max = 20, message = "메모는 최대 20개입니다.")
            List<@Size(max = 500) String> patientNotes,

            @Size(max = 2000, message = "2000자 이내로 입력해주세요.")
            String rawNote,

            @Valid
            LabelsMetaRequest extractedBy
    ) {}

    /**
     * 메모를 항목으로 나눠 달라는 요청. 화면 {@code 1p} 의 "AI로 정리하기".
     *
     * <p><b>저장하지 않는다.</b> 나눈 결과를 환자가 {@code 1q-1-E} 에서 고친 뒤
     * {@code POST /api/cards/{cardId}/visit} 로 따로 저장한다.
     *
     * @param labels 앞선 응답의 {@code labels} 를 그대로 돌려주면 <b>AI 가 모델을 부르지 않고</b>
     *               재조립만 한다. 환자가 줄을 옮길 때마다 다시 부르지 않기 위한 자리다
     */
    public record ClassifyMemoRequest(
            @NotBlank(message = "메모를 입력해주세요.")
            @Size(max = 2000, message = "2000자 이내로 입력해주세요.")
            String memo,

            LocalDate visitedOn,

            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            @Size(max = 200, message = "문장이 너무 많습니다.")
            Map<String, String> labels,

            /**
             * 모델을 부르지 말라는 표시. 온디바이스 1단계.
             *
             * <p>비우면 서버가 정합니다 — {@code labels} 가 있으면 어차피 모델을 안 부릅니다.
             * <b>{@code false} 를 명시하면 문장만 나눠 돌려줍니다.</b> 그때 {@code axes} 는
             * 비어 있고 {@code labels} 는 전부 {@code "none"} 입니다.
             */
            Boolean classify,

            /** 폰이 라벨을 붙였으면 무엇으로 붙였는지. {@code labels} 와 함께 보냅니다. */
            @Valid
            LabelsMetaRequest labelsMeta
    ) {}

    /**
     * 폰이 문장에 라벨을 붙였을 때 무엇으로 붙였는지.
     *
     * <p><b>안 보내면 기록에 "무엇이 나눴는지"가 안 남습니다.</b> 서버는 라벨만 받으므로
     * 알 길이 없고, 나중에 "어느 버전이 이상하게 나눴나"를 못 되짚습니다.
     *
     * <p>AI 계약이 필드 둘만 받습니다. 더 보내면 거부됩니다.
     */
    public record LabelsMetaRequest(
            @NotBlank(message = "모델 이름이 필요합니다.")
            @Size(max = 80, message = "모델 이름은 80자 이내입니다.")
            String modelId,

            @NotBlank(message = "프롬프트 버전이 필요합니다.")
            @Size(max = 40, message = "프롬프트 버전은 40자 이내입니다.")
            String promptVersion
    ) {}

    // ---------- 응답 ----------

    /**
     * 나눈 결과. 아직 저장되지 않았다.
     *
     * @param axes      항목 이름 → 항목. 그대로 {@code CreateVisitRequest.axes} 로 옮길 수 있다
     * @param sentences 메모를 문장으로 쪼갠 것. {@code labels} 의 키가 이 배열의 인덱스다
     * @param labels    문장 인덱스 → 항목 이름. <b>수정 저장 때 그대로 돌려보내면 모델을 안 부른다</b>
     */
    public record ClassifyMemoResponse(
            Map<String, Axis> axes,
            List<String> sentences,
            Map<String, String> labels,
            List<String> patientNotes,

            /** 재방문 시점. 저장 요청의 {@code followUp} 으로 그대로 옮기면 됩니다. */
            FollowUp followUp,

            /**
             * 무엇이 이 문장들을 나눴는지. 저장 요청의 {@code extractedBy} 로 옮겨 주세요.
             *
             * <p>서버가 나눴으면 {@code memo-small-v4} / {@code apac.amazon.nova-pro-v1:0},
             * 폰이 나눴으면 보내신 {@code labelsMeta} 가 그대로 돌아옵니다.
             */
            LabelsMeta extractedBy
    ) {}

    /** 와이어프레임의 요약 카드. */
    public record VisitResponse(
            Long visitId,
            Long cardId,
            String clinicName,
            LocalDate visitedOn,

            /** 항목 이름 → 항목. 순서는 나눈 그대로다. */
            Map<String, Axis> axes,

            /** 재방문 시점. 없으면 안쪽이 전부 비어 있습니다. */
            FollowUp followUp,

            List<String> patientNotes,
            String rawNote,

            /**
             * 무엇이 나눴는지. 환자가 직접 적었으면 안쪽이 비어 있습니다.
             *
             * <p>폰이 나눴으면 여기에 폰 모델이 찍힙니다 — {@code Qwen3-1.7B-Q4_0}.
             */
            LabelsMeta extractedBy
    ) {
        public static VisitResponse from(VisitRecord v) {
            Map<String, Axis> axes = new LinkedHashMap<>();
            v.axesByName().forEach((name, a) -> axes.put(name, Axis.from(a)));

            return new VisitResponse(
                    v.getId(),
                    // 카드를 지우면 끊긴다. 기록은 남는다.
                    v.getCard() == null ? null : v.getCard().getId(),
                    v.getClinicName(),
                    v.getVisitedOn(),
                    axes,
                    v.followUp(),
                    List.copyOf(v.getPatientNotes()),
                    v.getRawNote(),
                    v.extractedBy());
        }
    }

    /**
     * 기록 탭의 목록 항목 (화면 1j).
     *
     * <p>목록에는 원문({@code rawNote})도 항목도 담지 않는다. 증상·복용약이 섞인 긴 텍스트라
     * 목록마다 실어 나를 이유가 없다. 상세는 {@code GET /api/visits/{id}} 로 본다.
     */
    public record VisitSummary(
            Long visitId,
            /** 카드를 지우면 {@code null} 이 된다. 기록은 그대로 남는다. */
            Long cardId,

            /** 카드가 지워져도 남는다 — 만들 때 박아둔 제목으로 내려간다. */
            String cardTitle,
            String clinicName,
            LocalDate visitedOn,

            /**
             * 재방문 시점. <b>상세와 같은 모양</b>이라 파싱을 한 벌로 씁니다.
             *
             * <p>날짜만 주다가 객체로 바꿨습니다 — {@code approximate} 없이는 달력이
             * "2주 뒤"를 그날만 되는 것처럼 그립니다(화면 1r-1 · 1r-2).
             */
            FollowUp followUp
    ) {
        public static VisitSummary from(VisitRecord v) {
            return new VisitSummary(
                    v.getId(),
                    v.getCard() == null ? null : v.getCard().getId(),
                    // 카드가 지워져도 줄 제목은 남는다. 만들 때 박아둔 값으로 내려간다.
                    v.displayCardTitle(),
                    v.getClinicName(),
                    v.getVisitedOn(),
                    v.followUp());
        }
    }
}
