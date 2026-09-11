package com.jinryomate.backend.card.web;

import com.jinryomate.backend.card.dto.CardDtos.CardResponse;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.card.service.BriefingCardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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

                    **AI 를 다시 부르지 않습니다.** 카드는 문답 턴마다 응답에 딸려 와서 이미
                    보관돼 있습니다. 이 호출은 그 값으로 카드 행을 만들 뿐이라 빠릅니다.

                    **여러 번 불러도 카드가 늘지 않습니다.** 이미 만든 카드가 있으면 그것을
                    그대로 돌려줍니다. 화면을 다시 그리거나 네트워크가 끊겼다 이어져도 안전합니다.

                    **문답을 한 번도 하지 않았으면 400입니다.** 빈 카드를 만들어 두면 그대로
                    확정돼 의사에게 갈 수 있습니다.

                    **검증에 걸린 값은 `unknown` 으로 저장되고 `rejectedFields` 에 이름이 담깁니다.**
                    카드 생성 자체는 성공합니다 — 통째로 실패시키면 환자가 답한 문답이 날아갑니다.
                    앱은 `rejectedFields` 를 보고 "이 항목은 직접 채워주세요"라고 안내하면 됩니다.

                    서버가 보는 규칙: 축 값 80자, 질문 최대 3개·각 40자, 진료과는 정해진 13개 목록.
                    **`title` 은 검증하지 않습니다** — AI 가 부위 + 기간을 조합해 만드는 값이라
                    병명이 들어갈 경로가 없습니다.

                    환자 인적사항은 **이 시점 값이 카드에 박힙니다.** 나중에 프로필을 고쳐도
                    이미 만들어진 카드는 바뀌지 않습니다.
                    """)
    @PostMapping("/sessions/{sessionId}/card")
    public CardResponse generate(@AuthenticationPrincipal Long userId,
                                 @PathVariable Long sessionId) {
        return briefingCardService.generate(userId, sessionId);
    }

    @Operation(
            summary = "브리핑 카드 목록",
            description = """
                    기록 탭의 "브리핑 카드" 쪽입니다 (화면 1j). 최근 작성 순으로 옵니다.

                    `visited` 가 진료를 마쳤는지입니다. 카드의 `status`(DRAFT/CONFIRMED)와는
                    **다른 축**이라 컬럼을 두지 않고 진료 기록이 붙었는지로 판단합니다.

                    `clinicName` 은 병원명이 선택 입력이라 **진료를 마쳤어도 비어 있을 수 있습니다.**
                    "진료 완료" 뱃지는 `visited` 로 판단하세요.

                    목록에는 본문이 담기지 않습니다. 상세는 `GET /api/cards/{id}` 로 봅니다.
                    """)
    @GetMapping("/me/cards")
    public List<CardSummary> list(@AuthenticationPrincipal Long userId) {
        return briefingCardService.list(userId);
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
                    보낸 것만 바뀝니다. 축은 `axes` 에 `{axis, value}` 로 보냅니다.

                    **고친 축에는 `source: "PATIENT_EDIT"` 가 붙고 근거가 `"[환자 수정] …"` 이 됩니다.**
                    의사가 "환자가 말한 그대로"와 "나중에 고친 값"을 구별할 수 있어야 하기 때문입니다.
                    `value` 를 비우면 "모르겠다"(`UNKNOWN`)가 됩니다.

                    **제목과 진료과는 고칠 수 없습니다.** 제목은 AI 가 부위 + 기간을 조합해
                    만드는 값이고, 진료과는 환자가 짚은 부위의 속성입니다 — 증상에서 과를 고르는 것은
                    우리도 AI 도 하지 않기로 했습니다.

                    **카드에 없는 축은 보낼 수 없습니다(400).** 화면에 없는 것을 고칠 수는 없습니다.

                    **확정된 카드는 고치지 않습니다.** 대신 그 카드를 이어받은 **새 버전이 만들어지고**
                    수정이 거기 반영됩니다. 응답의 `cardId` 와 `version` 이 달라지므로 앱은 그 값으로 갈아타야 합니다.
                    의사가 이미 본 카드가 뒤바뀌면 안 되기 때문입니다.
                    """)
    @PatchMapping("/cards/{cardId}")
    public CardResponse update(@AuthenticationPrincipal Long userId,
                               @PathVariable Long cardId,
                               @Valid @RequestBody UpdateCardRequest request) {
        return briefingCardService.update(userId, cardId, request);
    }

    @Operation(
            summary = "카드 삭제",
            description = """
                    기록 목록(화면 `1j`)에서 카드를 지웁니다.

                    **문답까지 함께 지워집니다.** 증상 대화가 서버에 그대로 남아 있는데 카드만
                    지우면, 환자는 지웠다고 생각하면서 증상·복용약이 계속 보관됩니다.

                    **같은 문답에서 나온 카드는 버전을 가리지 않고 전부 지워집니다.** 환자에게는
                    한 장이고 버전은 서버 사정입니다.

                    딸린 것은 이렇게 갈립니다.

                    - **진료 기록은 함께 지워집니다** — 그 카드에 대한 기록이라 홀로 남을 수 없습니다
                    - **일정은 남고 연결만 끊깁니다** — 카드를 지웠다고 병원 예약까지 사라지면
                      환자가 진료를 놓칩니다

                    **확정·전달한 카드도 지울 수 있습니다.** 환자 본인의 민감정보이고, 이미 보여준
                    것을 되돌릴 수는 없어도 서버가 계속 들고 있을 이유는 없습니다.

                    **되돌릴 수 없습니다.** 앱에서 한 번 확인받고 부르세요.
                    """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/cards/{cardId}")
    public void delete(@AuthenticationPrincipal Long userId,
                       @PathVariable Long cardId) {
        briefingCardService.delete(userId, cardId);
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
