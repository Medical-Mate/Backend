package com.jinryomate.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.global.web.RequestIdFilter;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * AI 문답 서비스에 실제로 붙는 구현.
 *
 * <p>계약은 AI 저장소가 단일 원천이다({@code docs/api-previsit.md}). 여기서는 우리가
 * 지킬 것만 다룬다 — 서명, 재시도, {@code state} 불투명성.
 *
 * <p><b>본문을 직접 직렬화한다.</b> {@code RestClient} 에 객체를 넘기면 Jackson 이
 * 나중에 직렬화하므로, 그 바이트를 미리 알 수 없어 서명할 수가 없다. 그래서 여기서
 * {@code byte[]} 로 만들어 서명하고 같은 바이트를 그대로 보낸다.
 *
 * <p><b>{@code state} 는 열어보지 않는다.</b> 받은 JSON 을 문자열로 보관했다가 다음 턴에
 * 그대로 실어 보낸다. 내부 구조에 의존하는 순간 AI 쪽 변경이 우리를 깨뜨린다.
 */
@Slf4j
public class HttpAiTurnClient implements AiTurnClient {

    private static final String START_PATH = "/v1/previsit/sessions";
    private static final String TURN_PATH = "/v1/previsit/turns";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiSigner signer;

    public HttpAiTurnClient(RestClient restClient,
                            ObjectMapper objectMapper,
                            AiSigner signer) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.signer = signer;
    }

    /**
     * 문답을 시작한다.
     *
     * <p>부위를 함께 보내지 않는다. {@code selections} 에 부위를 어떤 모양으로 싣는지가
     * 아직 안 정해졌고(좌우를 어떻게 표현하는지), 형식을 지어내면 AI 가 조용히 무시하거나
     * 거부한다. 정해지면 여기에 붙인다.
     */
    @Override
    public AiTurnResult start(IntakeSession session) {
        ObjectNode body = objectMapper.createObjectNode();
        return call(START_PATH, body, "세션 시작");
    }

    @Override
    public AiTurnResult turn(IntakeSession session, String utterance) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("state", readState(session));
        body.put("utterance", utterance);
        return call(TURN_PATH, body, "턴");
    }

    /**
     * 보관해둔 {@code state} 를 JSON 으로 되돌린다.
     *
     * <p>값을 읽지는 않는다. 요청 본문에 객체로 실어야 해서 파싱만 한다.
     */
    private JsonNode readState(IntakeSession session) {
        String state = session.getAiState();
        if (state == null || state.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(state);
        } catch (Exception e) {
            // state 가 깨졌으면 이어갈 수 없다. 빈 것으로 보내면 AI 가 처음부터 다시 묻는데,
            // 그건 환자가 한 답을 전부 잃는 것이라 조용히 넘어가면 안 된다.
            log.error("보관한 state 를 읽을 수 없습니다 sessionId={}", session.getId());
            throw new ApiException(ErrorCode.INTERNAL, "문답을 이어갈 수 없습니다. 다시 시작해주세요.");
        }
    }

    /**
     * 서명해서 호출하고 결과를 푼다.
     *
     * <p><b>502 에만 한 번 재시도한다.</b> 같은 {@code state} 로 다시 부르는 것이라
     * 환자에게 질문이 더 나가지 않는다. {@code 422} 는 우리 요청이 규격을 벗어난 것이라
     * 다시 보내도 같은 답이 오고, {@code 503} 은 상대가 내려간 것이라 즉시 재시도가 의미 없다.
     */
    private AiTurnResult call(String path, ObjectNode body, String what) {
        String requestId = requestId();
        byte[] bytes = serialize(body);

        try {
            return post(path, bytes, requestId);
        } catch (RetryableAiException e) {
            log.warn("AI {} 실패, 1회 재시도 requestId={} status={}", what, requestId, e.status);
            try {
                return post(path, bytes, requestId);
            } catch (RetryableAiException retry) {
                log.error("AI {} 재시도도 실패 requestId={} status={}", what, requestId, retry.status);
                throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
            }
        }
    }

    private AiTurnResult post(String path, byte[] body, String requestId) {
        Instant now = Instant.now();
        String signature = signer.sign("POST", path, now, requestId, body);

        TurnResponse response;
        try {
            response = restClient.post()
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
                        log.error("AI 호출 실패 status={} path={} requestId={}", status, path, requestId);
                        throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
                    })
                    .body(TurnResponse.class);
        } catch (RetryableAiException | ApiException e) {
            throw e;
        } catch (Exception e) {
            // 연결 실패·타임아웃. 같은 state 로 한 번 더 시도할 값어치가 있다.
            throw new RetryableAiException(0);
        }

        if (response == null || response.reply() == null) {
            log.error("AI 응답에 reply 가 없습니다 path={} requestId={}", path, requestId);
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
        }

        // 응답의 request_id 가 우리가 보낸 것과 다르면 로그가 이어지지 않는다. 막지는 않는다 —
        // 답은 이미 정상이고, 여기서 실패시키면 추적 편의를 위해 환자를 막는 것이 된다.
        if (response.requestId() != null && !requestId.equals(response.requestId())) {
            log.warn("AI 가 다른 request_id 를 돌려줬습니다 sent={} received={}",
                    requestId, response.requestId());
        }

        String state = response.state() == null ? null : response.state().toString();
        return new AiTurnResult(response.reply(), response.ended(), response.endReason(), state);
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

    /** 같은 {@code state} 로 한 번 더 시도할 값어치가 있는 실패. */
    private static class RetryableAiException extends RuntimeException {
        private final int status;

        RetryableAiException(int status) {
            super(null, null, false, false);
            this.status = status;
        }
    }

    /**
     * 턴 응답에서 우리가 쓰는 것만 담는다.
     *
     * <p>{@code card} 와 {@code audit} 도 함께 오지만 아직 저장하지 않는다 — 카드 연동은
     * 별도 작업이다. 모르는 필드는 무시된다.
     */
    private record TurnResponse(
            String reply,
            boolean ended,
            @com.fasterxml.jackson.annotation.JsonProperty("end_reason") String endReason,
            @com.fasterxml.jackson.annotation.JsonProperty("request_id") String requestId,
            JsonNode state
    ) {}
}
