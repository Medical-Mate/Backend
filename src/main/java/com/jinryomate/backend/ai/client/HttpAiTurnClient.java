package com.jinryomate.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.ai.dto.PatientProfile;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * AI 문답 서비스에 실제로 붙는 구현.
 *
 * <p>계약은 AI 저장소가 단일 원천이다({@code docs/api-previsit.md}). 여기서는 우리가
 * 지킬 것만 다룬다 — 서명, 재시도, {@code state} 불투명성.
 *
 * <p>서명·재시도는 {@link AiHttpCaller} 가 한다. 진료 후 메모 쪽과 같은 규칙을 써야 해서
 * 따로 뺐다.
 *
 * <p><b>{@code state} 는 열어보지 않는다.</b> 받은 JSON 을 문자열로 보관했다가 다음 턴에
 * 그대로 실어 보낸다. 내부 구조에 의존하는 순간 AI 쪽 변경이 우리를 깨뜨린다.
 */
@Slf4j
public class HttpAiTurnClient implements AiTurnClient {

    private static final String START_PATH = "/v1/previsit/sessions";
    private static final String TURN_PATH = "/v1/previsit/turns";

    private final ObjectMapper objectMapper;
    private final AiHttpCaller caller;

    public HttpAiTurnClient(RestClient restClient,
                            ObjectMapper objectMapper,
                            AiSigner signer) {
        this.objectMapper = objectMapper;
        this.caller = new AiHttpCaller(restClient, objectMapper, signer);
    }

    /**
     * 문답을 시작한다.
     *
     * <p><b>부위는 여기서 보낸다. {@code selections} 가 아니다.</b> {@code selections} 는 문답
     * 중간에 고른 값(통증 강도 같은 칩)을 넣는 자리이고, 부위는 인체도에서 문답 시작 전에
     * 정해진다.
     *
     * <p>좌우는 코드에 박지 않고 {@code side} 로 따로 보낸다. {@code SUR:051}(어깨) 하나가
     * 좌우를 다 덮는다.
     */
    @Override
    public AiTurnResult start(IntakeSession session) {
        ObjectNode body = objectMapper.createObjectNode();
        if (session.getSiteNodeId() != null) {
            body.put("site_node_id", session.getSiteNodeId());
        }
        // 노드 id 가 없을 때를 위한 자리. 계약이 "site_node_id 가 있으면 무시한다" 고 해서
        // 둘 다 실어도 안전하다.
        //
        // <b>이걸 안 보내고 있었다.</b> 앱이 짚은 부위의 이름(siteText)은 보내는데 노드 id 는
        // 안 보내고 있어서, 우리가 그 이름을 들고만 있고 AI 에는 아무 부위도 안 넘겼다.
        // 그래서 AI 가 "어디가 불편하신지" 부터 다시 묻고, 짚은 곳과 다른 부위를 말해도
        // 되묻지 못했다. 이름만으로도 그 둘이 돈다.
        if (session.getSiteText() != null && !session.getSiteText().isBlank()) {
            body.put("site_label", session.getSiteText());
        }
        if (session.getSide() != null) {
            body.put("side", session.getSide().toContract());
        }
        if (session.getAiProfile() != null) {
            body.put("profile", session.getAiProfile().toContract());
        }
        return call(START_PATH, body, "세션 시작");
    }

    /**
     * 환자 발화를 넘기고 다음 질문을 받는다.
     *
     * <p>온디바이스 프로필이면 {@code extraction} 이 함께 온다. <b>열어보지 않고 그대로
     * 실어 보낸다</b> — 형식은 AI 계약이 정하고, 우리가 구조를 읽기 시작하면 AI 쪽 변경이
     * 우리를 깨뜨린다.
     *
     * <p>{@code extraction} 이 있어도 {@code utterance} 는 함께 보낸다. AI 가 근거를
     * 검증하려면 원문이 필요하다 — 발화가 외부 LLM 업체에 안 가는 것이지 AI 서버에
     * 안 가는 것이 아니다.
     */
    @Override
    public AiTurnResult turn(IntakeSession session, String utterance,
                             JsonNode extraction, JsonNode extractionMeta) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("state", readState(session));
        body.put("utterance", utterance);
        if (extraction != null && !extraction.isNull()) {
            body.set("extraction", extraction);
        }
        if (extractionMeta != null && !extractionMeta.isNull()) {
            body.set("extraction_meta", extractionMeta);
        }
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

    /** 서명·재시도는 {@link AiHttpCaller} 가 하고, 여기서는 응답만 푼다. */
    private AiTurnResult call(String path, ObjectNode body, String what) {
        TurnResponse response = caller.call(path, body, what, TurnResponse.class);

        if (response == null || response.reply() == null) {
            log.error("AI 응답에 reply 가 없습니다 path={}", path);
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
        }
        caller.warnIfRequestIdDiffers(response.requestId());

        String state = response.state() == null ? null : response.state().toString();
        String card = response.card() == null || response.card().isNull() ? null : response.card().toString();
        return new AiTurnResult(response.reply(), response.ended(), response.endReason(), state, card);
    }

    /**
     * 문답이 끝난 뒤 질문 후보를 받아온다.
     *
     * <p><b>발화를 보내지 않는다.</b> 이미 끝난 문답이라 다음 질문이 필요 없고, 카드도
     * 바뀌지 않는다. {@code question_candidates} 만 채워서 돌아온다.
     *
     * <p>건강정보는 <b>여기서만</b> 나간다. 매 턴 보내면 요청 본문에 그것이 남는 표면이
     * 스무 배가 된다.
     */
    @Override
    public AiTurnResult requestQuestionCandidates(IntakeSession session, PatientProfile profile) {
        ObjectNode body = objectMapper.createObjectNode();
        body.set("state", readState(session));
        body.put("question_candidates", true);

        if (profile != null) {
            ObjectNode patient = body.putObject("patient_profile");
            // 값이 null 인 항목은 키째 뺀다. 계약에 "모르겠다" 자리가 없어서,
            // 빈 배열로 넣으면 "없다"가 되어 확인 질문이 안 나간다.
            putIfPresent(patient, "medications", profile.medications());
            putIfPresent(patient, "conditions", profile.conditions());
            putIfPresent(patient, "allergies", profile.allergies());
        }

        return call(TURN_PATH, body, "질문 후보");
    }

    private void putIfPresent(ObjectNode node, String name, List<String> values) {
        if (values == null) {
            return;
        }
        ArrayNode array = node.putArray(name);
        values.forEach(array::add);
    }

    /**
     * 턴 응답에서 우리가 쓰는 것만 담는다.
     *
     * <p>{@code audit} 도 함께 오지만 저장하지 않는다 — 진단 추적용이라 환자 발화가 그대로
     * 들어 있고, 우리가 쓸 일이 없다. 모르는 필드는 무시된다.
     */
    private record TurnResponse(
            String reply,
            boolean ended,
            @com.fasterxml.jackson.annotation.JsonProperty("end_reason") String endReason,
            @com.fasterxml.jackson.annotation.JsonProperty("request_id") String requestId,
            JsonNode state,
            JsonNode card
    ) {}
}
