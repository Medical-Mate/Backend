package com.jinryomate.backend.card.web;

import com.jinryomate.backend.card.dto.CardDtos.CardResponse;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.card.service.BriefingCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "브리핑 카드", description = "문답 결과를 의사에게 전달할 카드 (화면 S3)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BriefingCardController {

    private final BriefingCardService briefingCardService;

    @Operation(
            summary = "카드 생성",
            description = """
                    문답을 카드로 만듭니다.

                    **검증에 걸린 필드는 `UNKNOWN` 으로 저장되고 `rejectedFields` 에 이름이 담깁니다.**
                    카드 생성 자체는 성공합니다 — 통째로 실패시키면 환자가 답한 문답이 날아갑니다.
                    앱은 `rejectedFields` 를 보고 "이 항목은 직접 채워주세요"라고 안내하면 됩니다.

                    서버가 보는 규칙: 제목 20자·진단명 금지, 각 텍스트 80자, 질문 최대 3개·각 40자.

                    환자 인적사항은 **이 시점 값이 카드에 박힙니다.** 나중에 프로필을 고쳐도
                    이미 만들어진 카드는 바뀌지 않습니다.
                    """)
    @PostMapping("/sessions/{sessionId}/card")
    public CardResponse generate(@AuthenticationPrincipal Long userId,
                                 @PathVariable Long sessionId) {
        return briefingCardService.generate(userId, sessionId);
    }

    @Operation(summary = "카드 조회")
    @GetMapping("/cards/{cardId}")
    public CardResponse get(@AuthenticationPrincipal Long userId,
                            @PathVariable Long cardId) {
        return briefingCardService.get(userId, cardId);
    }

    @Operation(
            summary = "카드 수정",
            description = """
                    보낸 필드만 바뀝니다.

                    **확정된 카드는 고치지 않습니다.** 대신 그 카드를 이어받은 **새 버전이 만들어지고**
                    수정이 거기 반영됩니다. 응답의 `cardId` 와 `version` 이 달라지므로 앱은 그 값으로 갈아타야 합니다.
                    의사가 이미 본 카드가 뒤바뀌면 안 되기 때문입니다.

                    환자가 넣은 값도 AI가 준 값과 똑같이 검증합니다. 제목에 진단명이 들어가면 막힙니다.
                    """)
    @PatchMapping("/cards/{cardId}")
    public CardResponse update(@AuthenticationPrincipal Long userId,
                               @PathVariable Long cardId,
                               @Valid @RequestBody UpdateCardRequest request) {
        return briefingCardService.update(userId, cardId, request);
    }

    @Operation(
            summary = "카드 확정",
            description = "확정 후에는 수정이 새 버전으로 남습니다. 이미 확정된 카드를 다시 확정하면 400입니다.")
    @PostMapping("/cards/{cardId}/confirm")
    public CardResponse confirm(@AuthenticationPrincipal Long userId,
                                @PathVariable Long cardId) {
        return briefingCardService.confirm(userId, cardId);
    }
}
