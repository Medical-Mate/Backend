package com.jinryomate.backend.visit.dto;

import com.jinryomate.backend.visit.entity.VisitRecord;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 진료 후 기록의 요청·응답. */
public final class VisitDtos {

    private VisitDtos() {}

    // ---------- 요청 ----------

    /**
     * 진료 직후 기록.
     *
     * <p>모든 항목이 선택이다. 병원을 막 나온 환자에게 필수 입력을 강요하면 아무것도 안 남는다.
     * 원문({@code rawNote})만 적어도 저장된다.
     */
    public record CreateVisitRequest(
            @Size(max = 60, message = "병원명은 60자 이내입니다.")
            String clinicName,

            LocalDate visitedOn,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String whatWasDone,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String result,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String prescription,

            @Size(max = 2000, message = "2000자 이내로 입력해주세요.")
            String rawNote
    ) {}

    // ---------- 응답 ----------

    /** 와이어프레임의 요약 카드. */
    public record VisitResponse(
            Long visitId,
            Long cardId,
            String clinicName,
            LocalDate visitedOn,
            String whatWasDone,
            String result,
            String prescription,
            String rawNote
    ) {
        public static VisitResponse from(VisitRecord v) {
            return new VisitResponse(
                    v.getId(),
                    // 카드를 지우면 끊긴다. 기록은 남는다.
                    v.getCard() == null ? null : v.getCard().getId(),
                    v.getClinicName(),
                    v.getVisitedOn(),
                    v.getWhatWasDone(),
                    v.getResult(),
                    v.getPrescription(),
                    v.getRawNote());
        }
    }

    /**
     * 기록 탭의 목록 항목 (화면 1j).
     *
     * <p>목록에는 원문({@code rawNote})을 담지 않는다. 증상·복용약이 섞인 긴 텍스트라
     * 목록마다 실어 나를 이유가 없다. 상세는 {@code GET /api/visits/{id}} 로 본다.
     */
    public record VisitSummary(
            Long visitId,
            /** 카드를 지우면 {@code null} 이 된다. 기록은 그대로 남는다. */
            Long cardId,

            /** 카드가 지워져도 남는다 — 만들 때 박아둔 제목으로 내려간다. */
            String cardTitle,
            String clinicName,
            LocalDate visitedOn
    ) {
        public static VisitSummary from(VisitRecord v) {
            return new VisitSummary(
                    v.getId(),
                    v.getCard() == null ? null : v.getCard().getId(),
                    // 카드가 지워져도 줄 제목은 남는다. 만들 때 박아둔 값으로 내려간다.
                    v.displayCardTitle(),
                    v.getClinicName(),
                    v.getVisitedOn());
        }
    }
}
