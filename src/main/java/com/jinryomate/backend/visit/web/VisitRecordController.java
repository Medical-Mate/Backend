package com.jinryomate.backend.visit.web;

import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.UpdateVisitRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
            summary = "진료 후 메모를 항목으로 나눈다",
            description = """
                    화면 `1p` 의 "AI로 정리하기"입니다. 환자가 적은 메모 한 덩이를
                    네 항목으로 나눠 돌려줍니다.

                    | 항목 | 화면 라벨 |
                    |---|---|
                    | `findings` | 소견 |
                    | `tests` | 검사 |
                    | `medication_instructions` | **약 · 생활 지시** |
                    | `follow_up` | 재방문 |

                    ⚠️ **`medication_instructions` 는 약만이 아닙니다.** AI 가 축 여섯을
                    넷으로 줄이면서 생활 지시·금지를 이 축에 접었습니다. 라벨을 "약"으로만
                    달면 그 줄에 `무거운 거 들지 말라고 하셨어요` 가 찍힙니다.

                    **저장하지 않습니다.** 나눈 결과를 `1q-1-E` 에서 고친 뒤
                    `POST /api/cards/{cardId}/visit` 로 따로 저장하세요.
                    카드에 매이지 않아 병원을 고른 직후에 바로 부를 수 있습니다.

                    응답의 `axes` 를 그대로 저장 요청의 `axes` 로 옮기시면 됩니다.

                    ### labels 를 돌려주세요

                    응답의 `labels`(문장 번호 → 항목 이름)를 그대로 다시 보내면
                    **모델을 부르지 않고** 재조립만 합니다. 환자가 `1q-1-E` 에서 줄을 옮길
                    때마다 이 API 를 부르실 거라면 반드시 함께 보내주세요 — 안 그러면
                    한 번 정리에 Bedrock 호출이 열 번 나갑니다.

                    `sentences` 의 인덱스가 `labels` 의 키입니다.
                    """)
    @PostMapping("/visits/classify")
    public ClassifyMemoResponse classify(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody ClassifyMemoRequest request) {
        return visitRecordService.classify(userId, request);
    }

    @Operation(
            summary = "진료 후 기록 저장",
            description = """
                    **확정한 카드에만** 남길 수 있고, 카드 하나에 기록 하나입니다.

                    모든 항목이 선택입니다. 병원을 막 나온 환자에게 필수 입력을 강요하면
                    아무것도 안 남습니다. `rawNote`(들은 이야기 원문)만 적어도 저장됩니다.

                    ### 항목은 칸이 고정이 아닙니다

                    `axes` 는 이름·값 목록입니다. AI 가 "소견"을 못 찾으면 그 줄이 없고,
                    다른 항목을 찾으면 그 줄이 생깁니다. 브리핑 카드의 `axes` 와 같은
                    모양이라 그리는 코드를 나눠 쓰실 수 있습니다.

                    `status` 와 `source` 는 보내지 않습니다. 값이 있으면 `FILLED`,
                    비었으면 `UNKNOWN` 이고 출처는 서버가 `PATIENT_EDIT` 로 박습니다.

                    ### 재방문 시점

                    `followUp` 은 날짜 하나가 아니라 셋입니다.

                    ```jsonc
                    "followUp": { "date": "2026-09-27", "text": "2주 뒤", "approximate": true }
                    ```

                    분류 응답의 `followUp` 을 그대로 옮기시면 됩니다. `approximate` 가
                    `true` 면 화면에 **"전후"** 를 붙이세요 — "2주 뒤" 는 날짜가 아니라
                    범위라, 시안은 `2주 뒤 (9월 27일 전후)` 로 찍습니다.

                    저장만 합니다. **캘린더 일정은 만들지 않습니다** —
                    `POST /api/me/appointments` 를 앱에서 따로 불러주세요. 환자가 보고
                    등록하는 흐름(`1r-2-A`)이고, AI 가 날짜를 잘못 뽑아도 조용히 일정이
                    생기지 않아야 합니다.

                    **녹음은 저장하지 않습니다.** 오디오 컬럼 자체가 없습니다.
                    """)
    @PostMapping("/cards/{cardId}/visit")
    public VisitResponse create(@AuthenticationPrincipal Long userId,
                                @PathVariable Long cardId,
                                @Valid @RequestBody CreateVisitRequest request) {
        return visitRecordService.create(userId, cardId, request);
    }

    @Operation(
            summary = "진료 후 기록 수정",
            description = """
                    화면 `1q-1-E` 의 "전체 수정"입니다.

                    `null` 인 필드는 건드리지 않습니다. 다만 **`axes` 는 보내면 통째로
                    갈아끼웁니다** — 환자가 줄을 지우면 그 항목이 사라져야 하는데,
                    병합이면 지운 줄이 남습니다. 화면에 남아 있는 줄을 전부 보내주세요.

                    카드와 달리 **확정 개념이 없습니다.** 이 기록은 환자 본인이 보는
                    메모라 잘못 적은 것을 못 고치게 할 이유가 없습니다.
                    """)
    @PatchMapping("/visits/{visitId}")
    public VisitResponse update(@AuthenticationPrincipal Long userId,
                                @PathVariable Long visitId,
                                @Valid @RequestBody UpdateVisitRequest request) {
        return visitRecordService.update(userId, visitId, request);
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
