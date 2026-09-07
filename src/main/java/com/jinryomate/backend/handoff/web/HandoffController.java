package com.jinryomate.backend.handoff.web;

import com.jinryomate.backend.handoff.dto.HandoffDtos.HandoffView;
import com.jinryomate.backend.handoff.service.HandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "진료실 전달", description = "환자가 자기 화면을 의사에게 보여줍니다 (화면 1f)")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HandoffController {

    private final HandoffService handoffService;

    @Operation(
            summary = "진료실 전달 화면",
            description = """
                    환자가 **자기 화면을 의사에게 보여주는** 형태입니다. QR 도 공유 링크도 쓰지 않습니다.

                    `GET /api/cards/{cardId}` 와 달리 `evidence`·`pipelineVersion` 같은
                    내부 추적값이 빠져 있습니다. 의사에게 AI 파이프라인 버전을 보여줄 이유가 없습니다.

                    **확정한 카드만** 열립니다. 초안이면 400입니다.

                    여는 순간이 전달 시각으로 기록되어, 나중에 "진료 어떠셨어요"(S5)를 물을 근거가 됩니다.
                    """)
    @GetMapping("/cards/{cardId}/handoff")
    public HandoffView open(@AuthenticationPrincipal Long userId, @PathVariable Long cardId) {
        return handoffService.open(userId, cardId);
    }
}
