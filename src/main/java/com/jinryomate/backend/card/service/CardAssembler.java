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
     * <p>대부분은 AI 가 준 카드 그대로이고, <b>통증 강도만 우리가 채운다</b>
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
     * 통증 강도 축을 우리가 채운다. 화면 3단계({@code 1d}) 슬라이더 값이다.
     *
     * <p><b>AI 로 보낼 길이 없어서 우리가 채운다.</b> 계약상 폼 값은 턴 요청의
     * {@code selections} 로 보내는데, 강도는 <b>문답이 끝난 뒤</b> 화면이라 그때는 이미
     * {@code ended} 다. 종료 뒤 턴은 마무리 문장만 돌려주고 카드를 건드리지 않는다 —
     * 실제로 보내 보고 확인했다. 그대로 두면 강도가 카드에 <b>영영 안 들어간다</b>.
     *
     * <p>모양은 계약의 {@code selection} 규약을 그대로 따른다 — 근거가
     * {@code "[선택] 3 (꽤 아파요)"} 이고 {@code source} 가 {@code SELECTION} 이다.
     * 우리가 만든 새 규칙이 아니다.
     *
     * <p><b>AI 가 종료 뒤 {@code selections} 를 받아주면 이 메서드를 지운다.</b> 문의해 뒀다.
     */
    private void fillSeverity(Map<String, CardAxis> axes, IntakeSession session) {
        if (session.getSeverityLevel() == null) {
            return;
        }

        // 라벨은 앱이 보낸 디자인 카피다. 없으면 숫자만 쓴다 — 우리가 문구를 만들지 않는다.
        String label = session.getSeverityLabel();
        String value = label == null || label.isBlank()
                ? String.valueOf(session.getSeverityLevel())
                : session.getSeverityLevel() + " (" + label + ")";

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
