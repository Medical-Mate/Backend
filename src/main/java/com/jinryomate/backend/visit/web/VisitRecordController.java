package com.jinryomate.backend.visit.web;

import com.jinryomate.backend.visit.dto.VisitDtos.AnswerRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.AnswerResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitResponse;
import com.jinryomate.backend.visit.service.VisitRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "진료 후 기록", description = "진료 직후 기록과 되묻기 (화면 S5)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VisitRecordController {

    private final VisitRecordService visitRecordService;

    @Operation(
            summary = "진료 후 기록 저장",
            description = """
                    병원을 나온 직후 골든타임에 들은 내용을 남깁니다.

                    **모든 항목이 선택입니다.** 병원을 막 나온 환자에게 필수 입력을 강요하면
                    아무것도 안 남습니다. `rawNote`(순서 없이 말한 원문)만 적어도 저장됩니다.

                    저장과 동시에 **되묻기 문항이 최대 3개 만들어집니다.** 기록에 실제로 있는
                    항목에서만 냅니다 — 받은 약이 없는데 "약은 몇 번 드세요?"를 묻지 않습니다.
                    그래서 문항이 0개일 수도 있습니다.

                    **카드가 확정된 뒤에만 남길 수 있습니다.** 진료를 마치고 쓰는 기록이라서요.
                    한 카드에 기록은 하나입니다.

                    녹음은 저장하지 않습니다. 기억 재구성 방식이라 오디오 컬럼 자체가 없습니다.
                    """)
    @PostMapping("/cards/{cardId}/visit")
    public VisitResponse create(@AuthenticationPrincipal Long userId,
                                @PathVariable Long cardId,
                                @Valid @RequestBody CreateVisitRequest request) {
        return visitRecordService.create(userId, cardId, request);
    }

    @Operation(
            summary = "요약 카드 조회",
            description = """
                    와이어프레임의 요약 카드입니다. 되묻기 진행 상황(`progress`)도 함께 옵니다.

                    문항의 정답은 내려보내지 않습니다. 답하기 전에 보이면 되묻기가 의미 없습니다.
                    """)
    @GetMapping("/visits/{visitId}")
    public VisitResponse get(@AuthenticationPrincipal Long userId,
                             @PathVariable Long visitId) {
        return visitRecordService.get(userId, visitId);
    }

    @Operation(
            summary = "되묻기 답변",
            description = """
                    **틀려도 막지 않습니다.** 시험이 아니라 이해 확인입니다.
                    `correct`가 `false`여도 `correction`에 정정 문구가 오니, 그대로 보여주고
                    다음 문항으로 넘어가면 됩니다.

                    같은 문항에 두 번 답하면 400입니다.
                    """)
    @PostMapping("/visits/{visitId}/checks/{checkId}/answer")
    public AnswerResponse answer(@AuthenticationPrincipal Long userId,
                                 @PathVariable Long visitId,
                                 @PathVariable Long checkId,
                                 @Valid @RequestBody AnswerRequest request) {
        return visitRecordService.answer(userId, visitId, checkId, request.answer());
    }
}
