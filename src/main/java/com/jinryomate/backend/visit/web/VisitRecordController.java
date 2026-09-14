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

                    ### 🔴 splitVersion 도 함께 돌려주세요

                    응답의 `splitVersion`(예: `"split-v2"`)을 들고 계시다가 **`labels` 를
                    되보낼 때 같이** 보내주세요.

                    **문장 번호가 라벨의 주소입니다.** AI 가 문장 나누는 규칙을 고치면 같은
                    메모가 다른 개수·다른 번호로 나뉩니다. 그 배포가 `1p`(메모 작성)와
                    `1q-2`(라벨 수정) 사이에 끼면 앱은 예전 번호로 매긴 라벨을 보내고 서버는
                    새 문장에 그 번호를 붙입니다 — **200 이 나가고 카드도 멀쩡해 보이는데
                    환자가 "약" 이라고 표시한 줄에 검사 얘기가 들어가 있습니다.** 조용히
                    틀린 진료 기록이 저장됩니다.

                    | 보낸 값이 | |
                    |---|---|
                    | 서버와 같으면 | 평소대로 |
                    | 서버와 다르면 | **409 `SPLIT_VERSION_CHANGED`** |
                    | 없으면 | 검사하지 않습니다 — 붙이기 전까지는 지금과 똑같이 동작합니다 |

                    **409 를 받으면** `labels` 를 빼고 같은 메모를 다시 보내세요. 새 `sentences`
                    와 새 `splitVersion` 이 옵니다. 환자가 고쳐 둔 라벨은 살릴 수 없습니다 —
                    문장 자체가 달라졌기 때문입니다. "다시 정리했어요" 를 띄워 주세요.

                    AI 를 새 이미지로 올리는 순간 `1p` 와 `1q-2` 사이에 떠 있던 세션은
                    이 409 를 한 번 받게 됩니다.

                    ### 온디바이스 — 폰이 분류할 때

                    한 엔드포인트를 세 가지로 씁니다.

                    | 보내는 것 | 하는 일 | 모델 |
                    |---|---|---|
                    | `memo` 만 | 서버가 나눈다 (기본) | 씁니다 |
                    | `memo` + `classify: false` | **문장만** 나눠 준다 | 안 씁니다 |
                    | `memo` + `labels` + `labelsMeta` | 그 라벨대로 조립 | 안 씁니다 |

                    **문장 분리는 항상 서버가 합니다.** 폰과 서버가 같은 번호를 봐야
                    `labels` 인덱스가 맞기 때문입니다. 폰에서 따로 쪼개지 마세요.

                    ```
                    ① POST /api/visits/classify  { memo, classify: false }
                       → sentences[3], axes 전부 NOT_ASKED, labels 전부 "none",
                         splitVersion "split-v2"
                    ② 폰 모델이 문장마다 라벨을 붙인다
                    ③ POST /api/visits/classify  { memo, labels, labelsMeta, splitVersion }
                       → 조립된 axes + followUp
                    ```

                    ①에서 받은 `splitVersion` 을 ③에 그대로 실어 보내세요. 온디바이스
                    경로는 ①과 ③ 사이에 폰 추론이 끼어 시간이 더 걸리므로, 그 사이에
                    규칙이 바뀔 틈도 그만큼 넓습니다.

                    `labelsMeta` 는 **폰 모델 정보**입니다(`Qwen3-1.7B-Q4_0` / `small-v4`).
                    안 보내시면 기록에 "무엇이 나눴는지"가 안 남습니다 — 서버는 라벨만
                    받으므로 알 길이 없고, 나중에 어느 버전이 이상하게 나눴는지 못 되짚습니다.

                    응답의 `extractedBy` 를 저장 요청의 `extractedBy` 로 그대로 옮겨 주세요.
                    """)
    @PostMapping("/visits/classify")
    public ClassifyMemoResponse classify(@AuthenticationPrincipal Long userId,
                                         @Valid @RequestBody ClassifyMemoRequest request) {
        return visitRecordService.classify(userId, request);
    }

    @Operation(
            summary = "진료 후 기록 저장",
            description = """
                    **확정한 카드에만** 남길 수 있습니다.

                    **카드 하나에 기록이 여럿 쌓입니다.** 같은 증상으로 재방문하면 그 진료의
                    기록을 또 남기세요 — 첫 기록을 덮어쓰지 않고 따로 쌓입니다. 모아 보려면
                    `GET /api/cards/{cardId}/visits` 를 쓰세요.

                    **같은 날 두 건도 받습니다.** 하루에 두 병원에 가는 일이 있어서 날짜로
                    막지 않습니다. 실수로 두 번 저장되지 않게 하는 것은 화면 몫입니다.

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

                    ### 무엇이 나눴는지

                    분류 응답의 `extractedBy` 를 그대로 옮겨 주세요. 폰이 나눴으면 폰 모델이
                    기록에 남습니다. 안 보내시면 비어 있고, 환자가 직접 적은 기록과
                    구별되지 않습니다.

                    **녹음은 저장하지 않습니다.** 오디오 컬럼 자체가 없습니다.
                    """)
    @PostMapping("/cards/{cardId}/visit")
    public VisitResponse create(@AuthenticationPrincipal Long userId,
                                @PathVariable Long cardId,
                                @Valid @RequestBody CreateVisitRequest request) {
        return visitRecordService.create(userId, cardId, request);
    }

    @Operation(
            summary = "이 카드로 다녀온 진료 전부",
            description = """
                    화면 `1j-3-R` (기록 상세 · 재방문 누적)의 **"진료 2회"** 가 이 목록입니다.
                    최근 진료일 순입니다.

                    **버전을 가리지 않고 모읍니다.** 재방문 전에 카드를 고치면 서버에서
                    버전이 올라가서 첫 기록과 두 번째 기록이 **서로 다른 카드 행**에 붙습니다.
                    `GET /api/me/visits` 를 `cardId` 로 걸러 모으시면 그때 한쪽이 빠집니다 —
                    게다가 목록이 드리는 `cardId` 는 **최신 버전 id** 라 고칠 때마다 바뀌어서
                    묶을 열쇠로 쓸 수 없습니다.

                    **체인의 아무 카드 id** 나 주시면 됩니다. 목록에서 받은 것을 그대로
                    넣으세요.
                    """)
    @GetMapping("/cards/{cardId}/visits")
    public List<VisitSummary> listByCard(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long cardId) {
        return visitRecordService.listByCard(userId, cardId);
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

                    `followUp` 은 상세와 같은 모양입니다. **`approximate` 가 `true` 면
                    달력에도 "전후" 를 붙여 주세요** — "2주 뒤" 를 그날만 되는 것처럼
                    그리면 안 됩니다(화면 1r-1 · 1r-2).

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
