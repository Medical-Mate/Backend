package com.jinryomate.backend.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.global.web.RequestIdFilter;
import java.time.Instant;
import java.util.UUID;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * AI 서비스에 서명해서 POST 하는 공통부.
 *
 * <p>진료 전 문답({@link HttpAiTurnClient})과 진료 후 메모({@link HttpAiMemoClient})가
 * 같은 서명·재시도 규칙을 쓴다. 규칙이 두 벌이면 한쪽만 고쳐지고, 그 결과는 401 이다.
 *
 * <p><b>본문을 직접 직렬화한다.</b> {@code RestClient} 에 객체를 넘기면 Jackson 이 나중에
 * 직렬화하므로 그 바이트를 미리 알 수 없어 서명할 수가 없다. 여기서 {@code byte[]} 로
 * 만들어 서명하고 같은 바이트를 그대로 보낸다.
 */
@Slf4j
class AiHttpCaller {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiSigner signer;

    AiHttpCaller(RestClient restClient, ObjectMapper objectMapper, AiSigner signer) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    /**
     * 서명해서 호출하고 응답을 {@code type} 으로 푼다.
     *
     * <p><b>502·504·연결 실패에만 한 번 재시도한다.</b> {@code 422} 는 우리 요청이 규격을
     * 벗어난 것이라 다시 보내도 같은 답이 오고, {@code 503} 은 상대가 내려간 것이라 즉시
     * 재시도가 의미 없다.
     *
     * <p>재시도에 <b>같은 {@code request_id} 와 같은 바이트</b>를 쓴다. 양쪽 로그가 한 건으로
     * 이어져야 한다.
     */
    <T> T call(String path, ObjectNode body, String what, Class<T> type) {
        return call(path, body, what, type, status -> null);
    }

    /**
     * 상태 코드 몇 개를 우리 오류로 옮겨 가며 호출한다.
     *
     * <p><b>기본은 4xx 를 전부 {@link ErrorCode#UPSTREAM_ERROR}(502) 로 뭉갭니다.</b>
     * 상류가 "네 요청이 잘못됐다"고 한 것을 앱에는 "AI 가 죽었다"로 전하는 셈인데, 대부분은
     * 그래도 됩니다 — 앱이 할 수 있는 일이 없으니까요.
     *
     * <p>할 수 있는 일이 있을 때만 {@code statusMap} 으로 갈라냅니다. 409(분리 규칙 바뀜)가
     * 그렇습니다 — 앱이 다시 분류하면 풀립니다.
     *
     * @param statusMap 이 상태 코드를 무엇으로 옮길지. {@code null} 을 주면 기본대로 간다
     */
    <T> T call(String path, ObjectNode body, String what, Class<T> type,
               IntFunction<ErrorCode> statusMap) {
        String requestId = requestId();
        byte[] bytes = serialize(body);

        try {
            return post(path, bytes, requestId, type, statusMap);
        } catch (RetryableAiException e) {
            log.warn("AI {} 실패, 1회 재시도 requestId={} status={}", what, requestId, e.status);
            try {
                return post(path, bytes, requestId, type, statusMap);
            } catch (RetryableAiException retry) {
                log.error("AI {} 재시도도 실패 requestId={} status={}", what, requestId, retry.status);
                throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
            }
        }
    }

    private <T> T post(String path, byte[] body, String requestId, Class<T> type,
                       IntFunction<ErrorCode> statusMap) {
        Instant now = Instant.now();
        String signature = signer.sign("POST", path, now, requestId, body);

        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(AiSigner.SIGNATURE_HEADER, signature)
                    .header(AiSigner.TIMESTAMP_HEADER, String.valueOf(now.getEpochSecond()))
                    .header(AiSigner.REQUEST_ID_HEADER, requestId)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        int status = res.getStatusCode().value();
                        if (status == 502 || status == 504) {
                            throw new RetryableAiException(status);
                        }
                        // 본문에 환자 발화가 되비쳐 올 수 있어 상태 코드만 남긴다.
                        // detail 도 마찬가지다 — AI 가 거기에 메모 조각을 실어 보낼 수 있다.
                        log.error("AI 호출 실패 status={} path={} requestId={}", status, path, requestId);

                        ErrorCode mapped = statusMap.apply(status);
                        throw mapped != null
                                ? new ApiException(mapped)
                                : new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
                    })
                    .body(type);
        } catch (RetryableAiException | ApiException e) {
            throw e;
        } catch (Exception e) {
            // 연결 실패·타임아웃. 같은 바이트로 한 번 더 시도할 값어치가 있다.
            throw new RetryableAiException(0);
        }
    }

    /**
     * 응답의 {@code request_id} 가 우리가 보낸 것과 다르면 로그가 이어지지 않는다.
     *
     * <p><b>막지는 않는다</b> — 답은 이미 정상이고, 여기서 실패시키면 추적 편의를 위해
     * 환자를 막는 것이 된다.
     */
    void warnIfRequestIdDiffers(String received) {
        String sent = RequestIdFilter.current();
        if (received != null && sent != null && !sent.equals(received)) {
            log.warn("AI 가 다른 request_id 를 돌려줬습니다 sent={} received={}", sent, received);
        }
    }

    ObjectNode newBody() {
        return objectMapper.createObjectNode();
    }

    private byte[] serialize(ObjectNode body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new IllegalStateException("AI 요청 본문을 만들 수 없습니다.", e);
        }
    }

    /** 웹 요청 밖(테스트·비동기)에서 불릴 수 있어 없으면 새로 만든다. */
    private String requestId() {
        String current = RequestIdFilter.current();
        return current != null ? current
                : "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** 같은 바이트로 한 번 더 시도할 값어치가 있는 실패. */
    private static class RetryableAiException extends RuntimeException {
        private final int status;

        RetryableAiException(int status) {
            super(null, null, false, false);
            this.status = status;
        }
    }
}
