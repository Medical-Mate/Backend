package com.jinryomate.backend.intake.web;

import com.jinryomate.backend.intake.dto.IntakeDtos.PhoneQuestionCandidatesRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionsRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SessionResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.SeverityRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "문답", description = "부위 짚기 → 통증 강도 → 문답·질문 고르기 (화면 1b · 1d · 1c)")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class IntakeSessionController {

    private final IntakeSessionService intakeSessionService;

    @Operation(
            summary = "문답 세션 시작",
            description = """
                    짚은 부위와 **통증 강도(`1d`)**를 함께 보내 문답을 시작합니다.
                    화면 순서가 `부위 짚기 → 통증 강도 → 문답·질문 고르기` 라 문답을 여는
                    시점에 둘 다 정해져 있습니다. 부위는 건너뛸 수 있습니다.

                    **`severity` 는 선택입니다.** `PUT /api/sessions/{id}/severity` 와 같은 모양
                    (`{"level": 3, "label": "꽤 아파요"}`)이고, 비우면 강도 없이 열립니다.
                    문답은 강도를 묻지 않습니다 — 슬라이더 값이 질문 후보와 카드에 들어갑니다.

                    **부위는 `siteNodeId` 하나입니다.** 부위 마스터(`body-map`)의 id 를 그대로
                    보냅니다 — `ANC:001`(앵커) · `SUR:032`(구역). 없는 id 는 400입니다.
                    부위 여러 개는 아직 정해지지 않았습니다.

                    **좌우는 코드에 박지 말고 `side` 로 보냅니다.** `LEFT` · `RIGHT` · `BOTH`.
                    `SUR:072`(손) 하나가 좌우를 다 덮습니다. **좌우가 없는 부위 13곳
                    (머리·배 등)에 보내면 400입니다** — 앱이 `body-map` 의 `laterality` 로
                    미리 거르면 안 봐도 됩니다. 부위 없이 좌우만 보내도 400입니다.

                    **나이·성별이 없으면 400입니다.** 이 둘은 의사용 카드 헤더에 반드시 찍혀서,
                    없으면 문답을 다 해도 카드가 성립하지 않습니다.
                    `GET /api/me/health-profile` 의 `canStartIntake` 로 미리 확인할 수 있습니다.

                    `siteText` 는 사람이 읽는 표현입니다. 예: `손(오른쪽)`.
                    """)
    @PostMapping
    public SessionResponse start(@AuthenticationPrincipal Long userId,
                                 @Valid @RequestBody StartSessionRequest request) {
        return intakeSessionService.start(userId, request);
    }

    @Operation(
            summary = "세션 조회",
            description = """
                    앱을 껐다 켜도 이어서 답할 수 있도록 대화와 진행도를 돌려줍니다.

                    **`questionCandidates` 는 AI 가 만든 "의사에게 물어볼 것" 후보입니다.**
                    문답 채팅 안의 질문 고르기(`1c-D`)가 이걸 보여줍니다. **문답이 끝나야
                    채워지고** 그 전에는 빈 배열입니다. `rank` 순으로 이미 정렬해 최대 3개만
                    내려줍니다. 서버 프로필이면 마지막 턴 응답에도 같은 값이 실립니다.

                    **최종 목록은 후보가 아닙니다.** 환자가 체크한 줄(후보 + 직접 쓴 것)을
                    `PUT /api/sessions/{id}/questions` 로 보내주세요.

                    `progress` 는 대화 턴 진행도이고 화면 단계(n/3)가 아닙니다. 화면 단계는
                    앱이 그립니다.
                    """)
    @GetMapping("/{sessionId}")
    public SessionResponse get(@AuthenticationPrincipal Long userId,
                               @PathVariable Long sessionId) {
        return intakeSessionService.get(userId, sessionId);
    }

    @Operation(
            summary = "답변 전송",
            description = """
                    환자 발화를 보내고 다음 질문을 받습니다. 문답 채팅(`1c`)이 이 API 를 씁니다.

                    한 번 호출에 대화가 **두 줄** 쌓입니다 — 보낸 발화와 AI 의 다음 질문입니다.
                    응답의 `messages` 에는 대화 전체가 들어 있어, 화면을 다시 그릴 때
                    세션을 또 조회하지 않아도 됩니다.

                    **음성으로 말했어도 오디오를 보내지 마세요.** 앱이 변환한 텍스트만 받습니다.
                    `inputMethod` 에 `STT` 를 넣으면 음성이었다는 것만 기록됩니다 —
                    녹음 자체는 저장하지 않습니다.

                    `ended` 가 true 면 문답이 끝난 것입니다. **그 뒤에 또 보내도 오류가 아닙니다** —
                    마지막 문장만 돌아오고 상태는 바뀌지 않습니다. 네트워크가 끊긴 사이에
                    끝났을 수 있어서, 앱이 종료 시점을 정확히 몰라도 되게 했습니다.

                    **`ended` 가 true 인 응답에는 `questionCandidates` 가 실립니다**(서버 프로필).
                    마지막 답 바로 아래에 질문 고르기(`1c-D`)를 그리면 됩니다. 그 밖에는
                    빈 배열입니다 — 문답 중, 온디바이스(폰이 만들어 `POST /question-candidates`
                    로 보냅니다), 후보 생성 실패.
                    """)
    @PostMapping("/{sessionId}/messages")
    public TurnResponse sendMessage(@AuthenticationPrincipal Long userId,
                                    @PathVariable Long sessionId,
                                    @Valid @RequestBody SendMessageRequest request) {
        return intakeSessionService.sendMessage(userId, sessionId, request);
    }

    @Operation(
            summary = "통증 강도 고치기",
            description = """
                    화면 `1d` 의 슬라이더 값입니다. **1~5 서열척도이고 NRS 0~10 이 아닙니다.**

                    **처음 고른 값은 세션 시작 요청의 `severity` 로 보내세요.** 이 API 는 문답 중이나
                    끝난 뒤에 `1d` 로 돌아가 고칠 때 씁니다. 다시 고르면 덮어씁니다.

                    `label` 은 "꽤 아파요" 같은 표시 문구인데 **앱이 보냅니다.** 서버가 들고
                    있으면 문구를 바꿀 때마다 배포해야 하고, 이건 디자인 카피라 서버 것이
                    아닙니다. 나중에 AI 에 넘길 때 `"3 (꽤 아파요)"` 를 조립하는 데 씁니다.

                    상태를 가리지 않습니다 — 진행 중이든 끝났든 받습니다. 카드에는 고친 값이
                    들어가지만, **이미 받은 질문 후보는 다시 만들지 않습니다.**
                    """)
    @PutMapping("/{sessionId}/severity")
    public SessionResponse recordSeverity(@AuthenticationPrincipal Long userId,
                                          @PathVariable Long sessionId,
                                          @Valid @RequestBody SeverityRequest request) {
        return intakeSessionService.recordSeverity(userId, sessionId, request);
    }

    @Operation(
            summary = "의사에게 물어볼 것",
            description = """
                    문답 채팅 안의 질문 고르기(`1c-D`)에서 **체크한 줄**입니다. **최대 5개**이고
                    순서가 화면에 번호로 보입니다.

                    **목록을 통째로 보내세요.** 추가·편집·삭제·순서변경이 전부 이 한 번으로
                    처리됩니다. 비우려면 빈 배열을 보내면 됩니다.

                    **끝난 문답에도 보낼 수 있습니다.** 질문 고르기는 문답이 끝난 뒤에 나옵니다.

                    AI 가 만든 후보는 `GET /api/sessions/{id}` 의 `questionCandidates` 로 옵니다.
                    **최종 목록은 여기로 보내는 이 값**입니다 — 체크한 후보와 직접 쓴 것을
                    합치는 것은 앱 몫입니다. 체크를 푼 줄은 저장되지 않습니다.
                    """)
    @PutMapping("/{sessionId}/questions")
    public SessionResponse replaceQuestions(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long sessionId,
                                            @Valid @RequestBody QuestionsRequest request) {
        return intakeSessionService.replaceQuestions(userId, sessionId, request);
    }

    @Operation(
            summary = "폰이 만든 질문 후보 보내기 (온디바이스)",
            description = """
                    폰 모델이 만든 "의사에게 물어볼 것" 후보를 보내면 **AI 가 검증하고 모자라면
                    채워서** 돌려줍니다. 외부 LLM 은 부르지 않습니다.

                    - 재료는 세션 응답의 `cardAxes`(카드 축 값·상태)와 `severity` 입니다
                    - 서버가 세션의 `severity` 를 AI 에 함께 넘겨, 검증·보충도 강도가 반영된 카드로 돕니다
                    - **폰이 하나도 못 만들었으면 빈 배열**을 보내세요. 카드 값으로 만든 템플릿으로 채웁니다
                    - 응답 `questionCandidates[].origin` 이 `model`(폰 후보 통과) · `template`(보충) 입니다
                    - **문답이 끝난 뒤에만** 보낼 수 있습니다. 아니면 400
                    - 다시 보내면 후보를 갈아끼웁니다

                    **온디바이스 세션은 문답이 끝나도 서버가 후보를 미리 만들지 않습니다.** 이 경로가
                    후보를 받는 유일한 길입니다.

                    ```json
                    {
                      "candidates": [
                        {"text": "계단 내려갈 때 더 아픈 건 괜찮은 건가요?", "source": "exacerbating_relieving"}
                      ],
                      "candidatesMeta": {"model_id": "qwen3.5-2b-q4_0", "prompt_version": "q-small-v4"}
                    }
                    ```
                    """)
    @PostMapping("/{sessionId}/question-candidates")
    public SessionResponse submitPhoneQuestionCandidates(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long sessionId,
                                                         @Valid @RequestBody PhoneQuestionCandidatesRequest request) {
        return intakeSessionService.submitPhoneQuestionCandidates(userId, sessionId, request);
    }
}
