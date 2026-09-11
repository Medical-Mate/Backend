package com.jinryomate.backend.intake.web;

import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SessionResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.TurnResponse;
import com.jinryomate.backend.intake.service.IntakeSessionService;
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

@Tag(name = "문답", description = "부위 짚기와 증상 문답 세션 (화면 S1.5 · S2)")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class IntakeSessionController {

    private final IntakeSessionService intakeSessionService;

    @Operation(
            summary = "문답 세션 시작",
            description = """
                    S1.5에서 짚은 부위와 함께 문답을 시작합니다. 부위는 건너뛸 수 있습니다.

                    **나이·성별이 없으면 400입니다.** 이 둘은 의사용 카드 헤더에 반드시 찍혀서,
                    없으면 문답을 다 해도 카드가 성립하지 않습니다.
                    `GET /api/me/health-profile` 의 `canStartIntake` 로 미리 확인할 수 있습니다.

                    `siteText` 는 사람이 읽는 표현입니다. 예: `손가락 관절(오른손)`.
                    문답 첫 문장에 그대로 들어갑니다.
                    """)
    @PostMapping
    public SessionResponse start(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody StartSessionRequest request) {
        return intakeSessionService.start(userId, request);
    }

    @Operation(
            summary = "세션 조회",
            description = "앱을 껐다 켜도 이어서 답할 수 있도록 대화와 진행도를 돌려줍니다.")
    @GetMapping("/{sessionId}")
    public SessionResponse get(@AuthenticationPrincipal Long userId,
                               @PathVariable Long sessionId) {
        return intakeSessionService.get(userId, sessionId);
    }

    @Operation(
            summary = "답변 전송",
            description = """
                    환자 발화를 보내고 다음 질문을 받습니다. 화면 `1c-1`~`1c-4` 가 이 API 를 씁니다.

                    한 번 호출에 대화가 **두 줄** 쌓입니다 — 보낸 발화와 AI 의 다음 질문입니다.
                    응답의 `messages` 에는 대화 전체가 들어 있어, 화면을 다시 그릴 때
                    세션을 또 조회하지 않아도 됩니다.

                    **음성으로 말했어도 오디오를 보내지 마세요.** 앱이 변환한 텍스트만 받습니다.
                    `inputMethod` 에 `STT` 를 넣으면 음성이었다는 것만 기록됩니다 —
                    녹음 자체는 저장하지 않습니다.

                    `ended` 가 true 면 문답이 끝난 것입니다. **그 뒤에 또 보내도 오류가 아닙니다** —
                    마지막 문장만 돌아오고 상태는 바뀌지 않습니다. 네트워크가 끊긴 사이에
                    끝났을 수 있어서, 앱이 종료 시점을 정확히 몰라도 되게 했습니다.
                    """)
    @PostMapping("/{sessionId}/messages")
    public TurnResponse sendMessage(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long sessionId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return intakeSessionService.sendMessage(userId, sessionId, request);
    }
}
