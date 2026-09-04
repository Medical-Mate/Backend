package com.jinryomate.backend.intake.web;

import com.jinryomate.backend.intake.dto.IntakeDtos.SessionResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
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
}
