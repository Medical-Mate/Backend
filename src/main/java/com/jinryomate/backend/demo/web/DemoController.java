package com.jinryomate.backend.demo.web;

import com.jinryomate.backend.demo.dto.DemoEventDtos.Accepted;
import com.jinryomate.backend.demo.dto.DemoEventDtos.EventBatch;
import com.jinryomate.backend.demo.service.DemoAiProxy;
import com.jinryomate.backend.demo.service.DemoEventService;
import com.jinryomate.backend.demo.service.DemoRateLimiter;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.hospital.client.HospitalSearchClient;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "웹 데모 (한시)", description = """
        스토어 제출이 불가해 만든 웹 데모 전용 경로입니다. **심사가 끝나면 지웁니다.**

        **인증이 없습니다.** 대신 호출 빈도를 제한합니다.

        **증상 내용을 저장하지 않습니다.** `state` 는 브라우저가 들고 다니세요 —
        AI 가 무상태라 가능한 구조입니다. 새로고침하면 문답이 사라집니다.

        서버에 남는 것은 `POST /events` 로 보낸 **익명 사용 기록**뿐입니다 —
        화면 이동과 응답 시간 같은 숫자이고, 발화 원문은 받지 않습니다.

        **AI 가 하는 일과 병원 찾기가 됩니다.** 브리핑 카드는 매 턴 응답에 들어 있어
        브라우저가 들고 그리면 됩니다 — 저장만 안 될 뿐입니다.

        **계정이 있어야 하는 것은 저장이 필요한 것들입니다** — 기록 목록·일정·홈 요약,
        그리고 확정한 카드의 버전 관리.
        """)
@Validated
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    /**
     * 시크릿이 없으면 이 빈이 없다(로컬·테스트). 그때 AI 통로는 503 이고
     * 부위 마스터는 그대로 나간다 — 화면을 만들어 보는 데는 AI 가 필요 없다.
     */
    private final ObjectProvider<DemoAiProxy> proxy;
    private final DemoRateLimiter rateLimiter;

    /** 앱이 쓰는 것과 같은 구현이다. 여기서는 문만 하나 더 낸다. */
    private final HospitalSearchClient hospitalSearchClient;

    private final DemoEventService eventService;

    @Operation(
            summary = "부위 마스터",
            description = """
                    부위를 고르는 화면(`1b`)에 쓸 34곳입니다. 앵커 9 + 구역 25.

                    **AI 를 부르지 않습니다.** 서버가 들고 있는 사본을 그대로 냅니다 —
                    이 값은 잘 안 바뀌고, 부위를 고르는 것만으로 비용이 나갈 이유가 없습니다.

                    `id` 를 그대로 `siteNodeId` 로 보내세요. `laterality` 가 `left_right` 인
                    노드만 좌우를 붙일 수 있습니다.
                    """)
    @GetMapping(value = "/body-map", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> bodyMap(HttpServletRequest request) {
        rateLimiter.check(request);
        return ResponseEntity.ok(new ClassPathResource("ontology/body-map.json"));
    }

    @Operation(
            summary = "문답 시작",
            description = """
                    AI 의 `POST /v1/previsit/sessions` 로 **본문을 그대로** 넘깁니다.
                    요청·응답 형식은 AI 계약을 그대로 따르세요.

                    응답의 `state` 를 브라우저가 보관했다가 다음 턴에 실어 보내면 됩니다.
                    """)
    @PostMapping(value = "/previsit/sessions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> startSession(HttpServletRequest request,
                                               @RequestBody(required = false) byte[] body) {
        rateLimiter.check(request);
        return aiProxy().forward("/v1/previsit/sessions", body);
    }

    @Operation(
            summary = "문답 턴",
            description = """
                    AI 의 `POST /v1/previsit/turns` 로 **본문을 그대로** 넘깁니다.
                    받은 `state` 로 덮어쓰며 진행하세요.

                    **질문 후보가 필요하면** 종료 턴에 `question_candidates: true` 를 얹으세요.
                    온보딩을 브라우저에 들고 계시면 `patient_profile` 도 같이 실으시면 됩니다 —
                    저희는 열어보지 않고 그대로 넘깁니다.

                    ### 오류

                    AI 가 준 상태 코드를 **그대로** 돌려드립니다. 뭉개지 않습니다.

                    | | |
                    |---|---|
                    | 422 | 스키마 위반 |
                    | 503 | 일일 LLM 예산 소진. 그날은 더 못 씁니다 |
                    | 429 | 호출이 너무 잦습니다 (이건 저희가 냅니다) |
                    """)
    @PostMapping(value = "/previsit/turns", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> turn(HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) {
        rateLimiter.check(request);
        return aiProxy().forward("/v1/previsit/turns", body);
    }

    @Operation(
            summary = "진료 후 메모 정리",
            description = """
                    AI 의 `POST /v1/postvisit/memo` 로 **본문을 그대로** 넘깁니다.
                    메모를 넣으면 네 항목(소견 · 검사 · 약·생활 지시 · 재방문)으로 나눠 옵니다.

                    ```jsonc
                    { "memo": "…", "visit_date": "2026-09-14", "clinic": "서울OO병원" }
                    ```

                    **카드가 필요 없습니다.** 진료 전 카드와 이어지지 않고, 넣은 메모와
                    돌려받은 결과만 있습니다.

                    **저장하지 않습니다.** 브라우저가 응답을 들고 있다가 새로고침하면
                    사라집니다. 목록도 이력도 없습니다 — 그것들은 계정이 있어야 합니다.

                    ### 라벨을 고쳐 다시 부르실 거라면

                    응답의 `labels` 를 되보내면 **모델을 부르지 않고** 재조립만 합니다.
                    그때는 `split_version` 도 **함께** 보내세요 — 그 사이 문장 분리 규칙이
                    바뀌었으면 AI 가 409 를 냅니다. 안 보내면 검사하지 않는 대신, 예전
                    번호가 새 문장에 붙어 **조용히 어긋난 결과**가 나옵니다.

                    ### 오류

                    AI 가 준 상태 코드를 **그대로** 돌려드립니다.

                    | | |
                    |---|---|
                    | 422 | 스키마 위반 · 빈 메모 · 2000자 초과 |
                    | 409 | `split_version` 이 서버 것과 다름 |
                    | 503 | 일일 LLM 예산 소진. 그날은 더 못 씁니다 |
                    | 429 | 호출이 너무 잦습니다 (이건 저희가 냅니다) |
                    """)
    @PostMapping(value = "/postvisit/memo", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> memo(HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) {
        rateLimiter.check(request);
        return aiProxy().forward("/v1/postvisit/memo", body);
    }

    @Operation(
            summary = "병원 검색",
            description = """
                    이름으로 병원을 찾습니다. **부분 일치**입니다.

                    **인증 경로(`GET /api/hospitals`)와 같은 것을 냅니다.** 앱이 쓰는 그 길은
                    그대로 두고, 여기에 문 하나를 더 낸 것뿐입니다.

                    **환자 데이터가 아닙니다.** 원천이 심평원 병원정보서비스이고 우리가 내는
                    것은 이름과 주소뿐입니다. 누가 무엇을 찾았는지 남기지 않습니다.

                    상류가 죽으면 **503** 입니다. 빈 목록으로 오지 않습니다 —
                    `{"hospitals":[],"totalCount":0}` 은 **정말로 그런 병원이 없는 것**입니다.
                    """)
    @GetMapping(value = "/hospitals", produces = MediaType.APPLICATION_JSON_VALUE)
    public HospitalSearchResponse hospitals(
            HttpServletRequest request,

            @RequestParam("q")
            @NotBlank(message = "검색어를 입력해주세요.")
            @Size(max = 60, message = "검색어는 60자 이내입니다.")
            String query,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "페이지는 1부터입니다.")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "1개 이상 요청해주세요.")
            @Max(value = 50, message = "한 번에 50개까지입니다.")
            int size) {
        rateLimiter.check(request);
        // AI 쪽과 달리 서명이 없고 우리 도메인 코드라, 바이트를 그대로 흘려보내는 것이
        // 아니라 평범한 위임이다. 캐시(결과 6시간·0건 5분)가 그 안에 이미 붙어 있다.
        return hospitalSearchClient.search(query, page, size);
    }

    @Operation(
            summary = "사용 이벤트 수집",
            description = """
                    화면 이동·이탈·응답 시간을 모읍니다. **환자 데이터가 아닙니다** —
                    증상·메모 원문, 검색어, 복용약 값은 받지 않습니다.

                    **배치로 보내세요.** 이벤트 하나에 요청 하나면 IP 당 30회/분에 바로
                    걸립니다. 문답 한 세션이 이미 호출 13회를 씁니다. 한 번에 50개까지입니다.

                    **카탈로그 밖은 400 입니다.** 이벤트 이름도, 속성 키도, 값의 모양도
                    서버가 정합니다. 하나라도 규격 밖이면 **묶음 전체를 거부합니다** —
                    좋은 것만 골라 넣으면 프론트 버그가 계속 숨습니다.

                    `sessionId` 는 **문답 세션 id 가 아닙니다.** 브라우저 세션마다 만드는
                    별도 난수를 쓰세요. 같은 값을 쓰면 이벤트가 카드와 이어져, 원문을 한
                    글자도 안 담아도 누가 무엇을 말했는지가 복원됩니다.

                    `occurredAt` 에는 **오프셋을 붙이세요**(`+09:00`). 없으면 400 입니다 —
                    조용히 UTC 로 해석해서 시간대 분석이 아홉 시간 틀리는 것보다 낫습니다.
                    """)
    @PostMapping(value = "/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Accepted> events(HttpServletRequest request,
                                           @Valid @RequestBody EventBatch batch) {
        rateLimiter.check(request);
        return ResponseEntity.accepted().body(new Accepted(eventService.accept(batch)));
    }

    private DemoAiProxy aiProxy() {
        DemoAiProxy found = proxy.getIfAvailable();
        if (found == null) {
            // 서명 없이 붙는 시늉을 하다 401 로 터지는 것보다 못 쓴다고 말하는 편이 낫다.
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "데모 문답을 지금 쓸 수 없습니다.");
        }
        return found;
    }
}
