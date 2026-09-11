package com.jinryomate.backend.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.dto.AiCard;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 세션에 보관해둔 AI 카드 원문을 {@link CardContent} 로 푼다.
 *
 * <p>파싱을 카드 만들 때 한 번만 하는 이유 — 카드는 매 턴 오는데 턴마다 풀어봐야 쓸 일이 없다.
 * 턴 처리 경로에서는 문자열로 덮어쓰기만 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardAssembler {

    private final ObjectMapper objectMapper;

    /** AI 계약의 SOCRATES 8축. 화면 순서이기도 하다. */
    private static final List<String> AXES = List.of(
            "site", "onset", "character", "radiation",
            "associated", "time_course", "exacerbating_relieving", "severity");

    public record Assembled(CardContent content, AiCard.Provenance provenance) {}

    /**
     * @param aiCardJson 세션에 보관해둔 원문
     * @param questions  환자가 4단계에서 적어둔 "물어볼 것". 카드에 스냅샷으로 박는다
     */
    public Assembled assemble(String aiCardJson, List<String> questions) {
        AiCard card = parse(aiCardJson);

        Map<String, CardAxis> axes = new LinkedHashMap<>();
        // 계약의 8축을 먼저 깔아둔다. AI 가 빠뜨린 축이 있어도 화면에 자리가 남아야
        // 환자가 "아직 안 물어봤구나"를 알 수 있다.
        AXES.forEach(name -> axes.put(name, CardAxis.notAsked(name)));

        if (card.axes() != null) {
            card.axes().forEach((name, axis) -> axes.put(name, toAxis(name, axis)));
        }

        List<String> departments = new ArrayList<>();
        String source = null;
        if (card.departmentGuidance() != null) {
            if (card.departmentGuidance().departments() != null) {
                departments.addAll(card.departmentGuidance().departments());
            }
            source = card.departmentGuidance().source();
        }

        CardContent content = new CardContent(
                card.title(),
                card.chiefComplaint(),
                axes,
                card.redFlags() == null ? List.of() : card.redFlags(),
                card.patientNotes() == null ? List.of() : card.patientNotes(),
                questions == null ? List.of() : questions,
                departments,
                source,
                card.completeness(),
                card.minimallyComplete());

        return new Assembled(content, card.provenance());
    }

    private AiCard parse(String json) {
        if (json == null || json.isBlank()) {
            // 문답을 한 번도 안 했거나 AI 가 카드를 안 준 경우다. 빈 카드를 만들어 주는 것보다
            // 막는 편이 낫다 — 빈 카드를 확정해서 의사에게 보여주면 안 된다.
            throw new ApiException(ErrorCode.INVALID_REQUEST, "아직 문답 내용이 없습니다.");
        }
        try {
            return objectMapper.readValue(json, AiCard.class);
        } catch (Exception e) {
            log.error("AI 카드를 읽을 수 없습니다: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.UPSTREAM_ERROR, "카드를 만들 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /**
     * 모르는 {@code status} 는 {@link AxisStatus#NOT_ASKED} 로 둔다.
     *
     * <p>거부하지 않는 이유 — AI 가 값을 하나 늘리면 카드가 통째로 실패한다. 축 하나를
     * "아직 안 물어봤다"로 두는 손해가, 문답이 날아가는 손해보다 작다.
     */
    private CardAxis toAxis(String name, AiCard.Axis axis) {
        if (axis == null) {
            return CardAxis.notAsked(name);
        }
        return CardAxis.of(
                name,
                parseEnum(AxisStatus.class, axis.status(), AxisStatus.NOT_ASKED, "status"),
                axis.value(),
                axis.evidence(),
                parseEnum(AxisSource.class, axis.source(), null, "source"));
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
}
