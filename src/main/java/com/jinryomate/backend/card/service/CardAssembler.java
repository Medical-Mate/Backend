package com.jinryomate.backend.card.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.dto.AiCard;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.entity.IntakeSession;
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
     * 세션에 쌓인 것을 카드 본문으로 만든다.
     *
     * <p>대부분은 AI 가 준 카드 그대로이고, <b>통증 강도만 우리가 다시 채운다</b>
     * ({@link #fillSeverity}).
     */
    public Assembled assemble(IntakeSession session) {
        AiCard card = parse(session.getAiCard());

        Map<String, CardAxis> axes = new LinkedHashMap<>();
        // 계약의 8축을 먼저 깔아둔다. AI 가 빠뜨린 축이 있어도 화면에 자리가 남아야
        // 환자가 "아직 안 물어봤구나"를 알 수 있다.
        AXES.forEach(name -> axes.put(name, CardAxis.notAsked(name)));

        if (card.axes() != null) {
            card.axes().forEach((name, axis) -> axes.put(name, toAxis(name, axis)));
        }

        fillSeverity(axes, session);

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
                session.getQuestions() == null ? List.of() : session.getQuestions(),
                departments,
                source,
                card.completeness(),
                card.minimallyComplete());

        return new Assembled(content, card.provenance());
    }

    /**
     * 통증 강도 축을 우리가 채운다. 화면 {@code 1d} 슬라이더 값이다.
     *
     * <p><b>AI 카드에도 강도가 들어가지만 여기서 한 번 더 채운다.</b> AI 에는 질문 후보를 받을
     * 때만 {@code selections} 로 실어 보낸다(#168). 그러니 후보 요청 <b>뒤에</b> 강도를 고쳤거나,
     * 후보 요청이 실패했거나, 끝나기 전에 카드를 만들면 AI 카드의 강도는 낡았거나 비어 있다.
     * 세션 값이 언제나 최신이라 이쪽이 최종이다.
     *
     * <p>모양은 계약의 {@code selection} 규약을 그대로 따른다 — 근거가
     * {@code "[선택] 3 (꽤 아파요)"} 이고 {@code source} 가 {@code SELECTION} 이다.
     * 값은 AI 에 보낸 것과 같은 {@link IntakeSession#severityValue()} 다.
     */
    private void fillSeverity(Map<String, CardAxis> axes, IntakeSession session) {
        String value = session.severityValue();
        if (value == null) {
            return;
        }

        axes.put("severity", CardAxis.of(
                "severity",
                AxisStatus.FILLED,
                value,
                List.of("[선택] " + value),
                AxisSource.SELECTION));
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
