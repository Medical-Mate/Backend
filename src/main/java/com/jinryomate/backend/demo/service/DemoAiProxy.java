package com.jinryomate.backend.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.AiProperties;
import com.jinryomate.backend.ai.client.AiSigner;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.global.web.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 브라우저가 보낸 본문을 <b>손대지 않고</b> 서명해서 AI 로 넘긴다.
 *
 * <p><b>바이트를 그대로 보낸다.</b> 받은 것을 객체로 풀었다가 다시 만들면 공백·키 순서가
 * 달라져 서명이 틀어지고, 우리가 모르는 필드가 조용히 떨어진다. 익명 데모는
 * {@code patient_profile} 을 브라우저가 직접 싣는데, 그게 사라지면 질문 후보가 환자
 * 정보를 못 쓴다 — AI 담당이 "떨어뜨리지 말아 달라"고 짚은 자리다.
 *
 * <p><b>본문을 열어보지 않는다.</b> {@code state} 는 계약이 불투명하게 다루라고 했고,
 * {@code card} 와 {@code patient_profile} 도 같은 규칙으로 지나간다. 여기서 구조를 읽기
 * 시작하면 AI 쪽 변경이 우리를 깨뜨린다. 예외는 {@code request_id} 하나인데, 그것도
 * <b>읽기만</b> 하고 본문은 건드리지 않는다.
 *
 * <p><b>로그에 본문을 남기지 않는다.</b> 증상 텍스트가 그대로 흐르는 통로다.
 */
@Slf4j
public class DemoAiProxy {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiSigner signer;

    public DemoAiProxy(RestClient restClient, ObjectMapper objectMapper, AiProperties properties) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.signer = new AiSigner(properties.hmacSecret());
    }

    /**
     * 서명해서 넘기고, AI 가 준 상태 코드와 본문을 그대로 돌려준다.
     *
     * <p><b>오류도 그대로 넘긴다.</b> 422(스키마 위반) · 503(예산 소진)이 무슨 뜻인지는
     * AI 계약이 정하고 브라우저가 그 계약을 보고 붙는다. 우리가 502 로 뭉개면 "예산이
     * 찼다"와 "AI 가 죽었다"를 구별할 방법이 없어진다.
     *
     * @param path AI 경로. 호출부가 <b>정해진 값만</b> 넘긴다 — 사용자가 준 문자열을
     *             여기에 그대로 흘리면 AI 의 아무 경로나 부르게 된다
     */
    public ResponseEntity<byte[]> forward(String path, byte[] body) {
        byte[] payload = body == null ? new byte[0] : body;
        String requestId = requestId(payload);
        Instant now = Instant.now();
        String signature = signer.sign("POST", path, now, requestId, payload);

        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AiSigner.SIGNATURE_HEADER, signature)
                    .header(AiSigner.TIMESTAMP_HEADER, String.valueOf(now.getEpochSecond()))
                    .header(AiSigner.REQUEST_ID_HEADER, requestId)
                    .body(payload)
                    .retrieve()
                    // 4xx·5xx 를 예외로 만들지 않는다. 상태와 본문을 그대로 넘기는 것이 일이다.
                    .onStatus(status -> false, (req, res) -> { })
                    .toEntity(byte[].class);

            log.info("데모 프록시 path={} status={} requestId={}",
                    path, response.getStatusCode().value(), requestId);

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (Exception e) {
            // 연결 실패·타임아웃. 예외 메시지에 본문이 실릴 수 있어 종류만 남긴다.
            log.error("데모 프록시 실패 path={} requestId={} type={}",
                    path, requestId, e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
        }
    }

    /**
     * 본문 최상위 {@code request_id} 를 <b>읽기만</b> 한다.
     *
     * <p>서명에 들어가는 값이라 헤더와 본문이 같아야 한다. 브라우저가 안 실었으면 우리가
     * 만들어 헤더에만 넣는다 — 본문에 넣으면 바이트가 달라져 서명이 틀어진다. 그때는 양쪽
     * 로그가 안 이어지지만, 문답을 막을 일은 아니다.
     */
    private String requestId(byte[] body) {
        if (body.length > 0) {
            try {
                JsonNode found = objectMapper.readTree(body).path("request_id");
                if (found.isTextual() && !found.asText().isBlank()) {
                    return found.asText();
                }
            } catch (Exception e) {
                // 본문이 JSON 이 아니면 AI 가 422 로 거절한다. 여기서 막지 않는다.
                log.warn("데모 요청 본문에서 request_id 를 읽지 못했습니다");
            }
        }
        String current = RequestIdFilter.current();
        return current != null ? current
                : "demo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
