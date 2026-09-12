package com.jinryomate.backend.visit.web;

import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitSummary;
import com.jinryomate.backend.visit.service.VisitRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "진료 후 기록", description = "진료를 마치고 남기는 기록 (S5)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VisitRecordController {

    private final VisitRecordService visitRecordService;

    @Operation(
            summary = "진료 후 기록 저장",
            description = """
                    **확정한 카드에만** 남길 수 있고, 카드 하나에 기록 하나입니다.

                    모든 항목이 선택입니다. 병원을 막 나온 환자에게 필수 입력을 강요하면
                    아무것도 안 남습니다. `rawNote`(들은 이야기 원문)만 적어도 저장됩니다.

                    **녹음은 저장하지 않습니다.** 오디오 컬럼 자체가 없습니다.
                    """)
    @PostMapping("/cards/{cardId}/visit")
    public VisitResponse create(@AuthenticationPrincipal Long userId,
                                @PathVariable Long cardId,
                                @Valid @RequestBody CreateVisitRequest request) {
        return visitRecordService.create(userId, cardId, request);
    }

    @Operation(summary = "진료 후 기록 조회", description = "와이어프레임의 요약 카드입니다.")
    @GetMapping("/visits/{visitId}")
    public VisitResponse get(@AuthenticationPrincipal Long userId,
                             @PathVariable Long visitId) {
        return visitRecordService.get(userId, visitId);
    }

    @Operation(
            summary = "진료 기록 삭제",
            description = """
                    기록 목록(화면 `1j`)에서 기록 한 건을 지웁니다.

                    **카드는 그대로 남습니다.** 카드까지 지우려면 `DELETE /api/cards/{id}` 를
                    따로 부르세요 — 그쪽은 문답도 함께 지웁니다.

                    되돌릴 수 없습니다.
                    """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/visits/{visitId}")
    public void delete(@AuthenticationPrincipal Long userId,
                       @PathVariable Long visitId) {
        visitRecordService.delete(userId, visitId);
    }

    @Operation(
            summary = "진료 기록 목록",
            description = """
                    기록 탭의 "진료 기록" 쪽입니다 (화면 1j). 최근 진료일 순으로 옵니다.

                    목록에는 원문(`rawNote`)이 담기지 않습니다. 증상·복용약이 섞인 긴 텍스트라
                    목록마다 실어 나를 이유가 없습니다. 상세는 `GET /api/visits/{id}` 로 봅니다.

                    `cardId` 는 **연결된 카드를 지우면 `null` 이 됩니다.** 기록은 그대로 남고
                    `cardTitle` 도 그대로입니다 — 만들 때 박아둔 제목으로 내려갑니다.

                    월별 그룹(`2026년 9월`)은 앱이 묶습니다.
                    """)
    @GetMapping("/me/visits")
    public List<VisitSummary> list(@AuthenticationPrincipal Long userId) {
        return visitRecordService.list(userId);
    }
}
