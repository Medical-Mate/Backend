package com.jinryomate.backend.visit.dto;

import com.jinryomate.backend.visit.entity.ComprehensionCheck;
import com.jinryomate.backend.visit.entity.VisitRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

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

    public record AnswerRequest(
            @NotBlank(message = "답변이 필요합니다.")
            @Size(max = 500, message = "500자 이내로 입력해주세요.")
            String answer
    ) {}

    // ---------- 응답 ----------

    /**
     * @param correct    맞았는지. 틀려도 다음 문항으로 넘어간다
     * @param correction 환자에게 보여줄 정정 문구
     */
    public record AnswerResponse(Long checkId, boolean correct, String correction, Progress progress) {}

    public record Progress(long answered, int total) {}

    public record CheckResponse(Long checkId, int seq, String question, String userAnswer, Boolean correct) {
        public static CheckResponse from(ComprehensionCheck c) {
            // 정답(expectedAnswer)은 내려보내지 않는다. 답하기 전에 보이면 되묻기가 의미 없다.
            return new CheckResponse(c.getId(), c.getSeq(), c.getQuestion(),
                    c.getUserAnswer(), c.getCorrect());
        }
    }

    /** 와이어프레임의 요약 카드. */
    public record VisitResponse(
            Long visitId,
            Long cardId,
            String clinicName,
            LocalDate visitedOn,
            String whatWasDone,
            String result,
            String prescription,
            String rawNote,
            List<CheckResponse> checks,
            Progress progress
    ) {
        public static VisitResponse from(VisitRecord v) {
            return new VisitResponse(
                    v.getId(),
                    v.getCard().getId(),
                    v.getClinicName(),
                    v.getVisitedOn(),
                    v.getWhatWasDone(),
                    v.getResult(),
                    v.getPrescription(),
                    v.getRawNote(),
                    v.getChecks().stream().map(CheckResponse::from).toList(),
                    new Progress(v.answeredCount(), v.getChecks().size()));
        }
    }
}
