package com.jinryomate.backend.demo.web;

import com.jinryomate.backend.demo.service.DemoAiProxy;
import com.jinryomate.backend.demo.service.DemoRateLimiter;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "웹 데모 (한시)", description = """
        스토어 제출이 불가해 만든 웹 데모 전용 경로입니다. **심사가 끝나면 지웁니다.**

        **인증이 없습니다.** 대신 호출 빈도를 제한합니다.

        **아무것도 저장하지 않습니다.** `state` 는 브라우저가 들고 다니세요 —
        AI 가 무상태라 가능한 구조입니다. 새로고침하면 문답이 사라집니다.

        **문진까지만 됩니다.** 브리핑 카드·진료 기록·일정은 계정이 있어야 합니다.
        """)
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

    private DemoAiProxy aiProxy() {
        DemoAiProxy found = proxy.getIfAvailable();
        if (found == null) {
            // 서명 없이 붙는 시늉을 하다 401 로 터지는 것보다 못 쓴다고 말하는 편이 낫다.
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "데모 문답을 지금 쓸 수 없습니다.");
        }
        return found;
    }
}
