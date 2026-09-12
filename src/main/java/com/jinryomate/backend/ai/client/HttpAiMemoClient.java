package com.jinryomate.backend.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

/**
 * 진료 후 메모 분류에 실제로 붙는 구현.
 *
 * <p>서명·재시도는 {@link AiHttpCaller} 가 한다. 여기서는 응답을 우리 축으로 옮기는 일만 한다.
 *
 * <p><b>모르는 값이 와도 막지 않는다.</b> 카드 쪽과 같은 방침이다 — AI 가 축이나 상태를 하나
 * 늘리면 분류가 통째로 실패한다. 축 하나를 "아직 안 물어봤다"로 두는 손해가, 환자가 방금 적은
 * 메모가 날아가는 손해보다 작다.
 */
@Slf4j
public class HttpAiMemoClient implements AiMemoClient {

    private static final String MEMO_PATH = "/v1/postvisit/memo";

    private final ObjectMapper objectMapper;
    private final AiHttpCaller caller;

    public HttpAiMemoClient(RestClient restClient, ObjectMapper objectMapper, AiSigner signer) {
        this.objectMapper = objectMapper;
        this.caller = new AiHttpCaller(restClient, objectMapper, signer);
    }

    @Override
    public MemoClassification classify(String memo, LocalDate visitedOn, String clinicName,
                                       Map<String, String> labels) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("memo", memo);
        if (visitedOn != null) {
            body.put("visit_date", visitedOn.toString());
        }
        if (clinicName != null && !clinicName.isBlank()) {
            body.put("clinic", clinicName);
        }
        if (labels != null && !labels.isEmpty()) {
            // 라벨이 있으면 AI 가 모델을 부르지 않고 조립만 한다.
            ObjectNode node = body.putObject("labels");
            labels.forEach(node::put);
            body.put("classify", false);
        }

        MemoResponse response = caller.call(MEMO_PATH, body, "메모 분류", MemoResponse.class);
        if (response == null || response.card() == null) {
            log.error("AI 메모 응답에 card 가 없습니다");
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "잠시 후 다시 시도해주세요.");
        }
        caller.warnIfRequestIdDiffers(response.requestId());

        return toClassification(response);
    }

    private MemoClassification toClassification(MemoResponse response) {
        JsonNode card = response.card();

        Map<String, CardAxis> axes = new LinkedHashMap<>();
        card.path("axes").fields().forEachRemaining(e -> axes.put(e.getKey(), toAxis(e.getKey(), e.getValue())));

        // 어느 축에도 안 들어간 문장. 계약이 두 자리에 나눠 담아서 둘 다 모은다.
        List<String> notes = new ArrayList<>();
        card.path("patient_notes").forEach(n -> notes.add(n.asText()));
        card.path("unsorted").forEach(n -> notes.add(n.asText()));

        return new MemoClassification(
                axes,
                textList(response.sentences()),
                response.labels() == null ? Map.of() : response.labels(),
                notes,
                toFollowUp(card.path("follow_up_date")),
                card.path("provenance").path("prompt_version").asText(null),
                card.path("provenance").path("model_id").asText(null));
    }

    /**
     * 재방문 시점을 읽는다.
     *
     * <p><b>객체다. 날짜 문자열이 아니다.</b>
     *
     * <pre>{@code
     * {"text": "2주 뒤", "date": "2026-09-27", "approximate": true,
     *  "basis": "visit_date 2026-09-13 + 14d"}
     * }</pre>
     *
     * <p>문자열로 와도 견딘다 — 계약이 바뀌었을 때 날짜를 통째로 잃는 것보다 낫다.
     *
     * <p>{@code basis} 는 버린다. 화면에 쓰는 곳이 없는 내부 계산 근거다.
     */
    private FollowUp toFollowUp(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return FollowUp.NONE;
        }
        if (node.isTextual()) {
            LocalDate date = parseDate(node.asText());
            return date == null ? FollowUp.NONE : new FollowUp(date, null, false);
        }
        if (!node.isObject()) {
            log.warn("AI 가 모르는 모양의 재방문 시점을 줬습니다: {}", node.getNodeType());
            return FollowUp.NONE;
        }

        FollowUp followUp = new FollowUp(
                parseDate(node.path("date").asText(null)),
                node.path("text").asText(null),
                node.path("approximate").asBoolean(false));

        // 키는 있는데 안이 비었으면 재방문 얘기가 없었던 것과 같다.
        return followUp.isEmpty() ? FollowUp.NONE : followUp;
    }

    private CardAxis toAxis(String name, JsonNode node) {
        if (node == null || node.isNull()) {
            return CardAxis.notAsked(name);
        }
        List<String> evidence = new ArrayList<>();
        node.path("evidence").forEach(e -> evidence.add(e.asText()));

        return CardAxis.of(
                name,
                parseEnum(AxisStatus.class, node.path("status").asText(null), AxisStatus.NOT_ASKED, "status"),
                node.path("value").asText(null),
                evidence,
                parseEnum(AxisSource.class, node.path("source").asText(null), null, "source"));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, String what) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("AI 가 모르는 {} 값을 줬습니다: {}", what, raw);
            return fallback;
        }
    }

    /**
     * 날짜 문자열을 읽는다. 못 읽으면 비운다.
     *
     * <p>여기서 실패시키면 나머지 분류까지 버리게 된다. 재방문 날짜는 환자가 1q-1 에서
     * 눈으로 보고 고칠 수 있는 값이라 비어 있어도 흐름이 끊기지 않는다.
     */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            log.warn("AI 가 읽을 수 없는 재방문 날짜를 줬습니다: {}", raw);
            return null;
        }
    }

    private List<String> textList(List<String> raw) {
        return raw == null ? List.of() : List.copyOf(raw);
    }

    /**
     * 메모 응답에서 우리가 쓰는 것만 담는다.
     *
     * <p>{@code dropped} · {@code usage} 도 오지만 저장하지 않는다. 모르는 필드는 무시된다.
     *
     * <p>{@code card} 를 {@code JsonNode} 로 받는 이유 — 안에 {@code widening} ·
     * {@code site_comparison} · {@code document_codes} 처럼 아직 화면이 없는 값이 섞여 있다.
     * 지금 전부 타입으로 박으면 AI 가 하나 바꿀 때마다 우리가 깨진다.
     */
    private record MemoResponse(
            JsonNode card,
            List<String> sentences,
            Map<String, String> labels,
            @JsonProperty("request_id") String requestId
    ) {}
}
