package com.jinryomate.backend.handoff.web;

import com.jinryomate.backend.handoff.dto.HandoffDtos.HandoffView;
import com.jinryomate.backend.handoff.dto.HandoffDtos.ShareLinkResponse;
import com.jinryomate.backend.handoff.service.HandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "진료실 전달·공유", description = "카드를 의사에게 보여주고(S4) 링크로 공유합니다(S6)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HandoffController {

    private final HandoffService handoffService;

    @Operation(
            summary = "진료실 전달 화면",
            description = """
                    환자가 **자기 화면을 의사에게 보여주는** 형태입니다. QR 은 쓰지 않습니다.

                    `GET /api/cards/{cardId}` 와 달리 `evidence`·`pipelineVersion` 같은
                    내부 추적값이 빠져 있습니다. 의사에게 AI 파이프라인 버전을 보여줄 이유가 없습니다.

                    **확정한 카드만** 열립니다. 초안이면 400입니다.

                    여는 순간이 전달 시각으로 기록되어, 나중에 "진료 어떠셨어요"(S5)를 물을 근거가 됩니다.
                    """)
    @GetMapping("/cards/{cardId}/handoff")
    public HandoffView open(@AuthenticationPrincipal Long userId, @PathVariable Long cardId) {
        return handoffService.open(userId, cardId);
    }

    @Operation(
            summary = "공유 링크 발급",
            description = """
                    가족에게 보내거나 다음 방문 때 다시 열기 위한 링크입니다.

                    **인증 없이 열리는 주소라 만료가 유일한 방어선입니다.** 기본 수명은 24시간이고
                    발급할 때마다 새 토큰이 나옵니다. 화면에 들어올 때마다 발급하면 항상 신선합니다.

                    잘못 보냈다면 `DELETE /api/share-links/{id}` 로 즉시 끊을 수 있습니다.
                    """)
    @PostMapping("/cards/{cardId}/share")
    public ShareLinkResponse issue(@AuthenticationPrincipal Long userId, @PathVariable Long cardId) {
        return handoffService.issueShareLink(userId, cardId);
    }

    @Operation(summary = "내가 만든 공유 링크 목록", description = "최근 발급 순입니다. 열람 시각과 횟수가 함께 옵니다.")
    @GetMapping("/me/share-links")
    public List<ShareLinkResponse> list(@AuthenticationPrincipal Long userId) {
        return handoffService.listShareLinks(userId);
    }

    @Operation(summary = "공유 링크 폐기", description = "즉시 열리지 않게 됩니다. 이미 폐기된 링크를 다시 폐기해도 204입니다.")
    @DeleteMapping("/share-links/{shareLinkId}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long shareLinkId) {
        handoffService.revoke(userId, shareLinkId);
        return ResponseEntity.noContent().build();
    }
}
