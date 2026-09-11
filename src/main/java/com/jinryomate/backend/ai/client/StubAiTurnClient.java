package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;

/**
 * AI 서비스에 실제로 붙기 전까지 쓰는 임시 구현.
 *
 * <p><b>질문이 고정이다.</b> 환자가 무엇을 답하든 다음 축을 순서대로 물을 뿐, 답변을
 * 읽지 않는다. 그래도 턴 처리 → 메시지 적재 → 진행도 → 종료 판정 → {@code state}
 * 왕복은 실제 경로로 돌아가므로, 앱이 S2 화면을 만들고 검증할 수 있다.
 *
 * <p><b>{@code MEDIMATE_HMAC_SECRET} 이 비어 있을 때만 쓰인다.</b> 빈 시크릿으로 서명하면
 * AI 가 요청을 전부 거부하므로, 그때는 붙는 시늉을 하는 것보다 스텁이 도는 편이 낫다.
 * 로컬 개발과 테스트에서는 시크릿이 없는 것이 정상이다. 어느 쪽이 물렸는지는 기동 로그에
 * 남는다 — {@link com.jinryomate.backend.ai.AiClientConfig} 참고.
 *
 * <p>빈 등록은 {@code AiClientConfig} 가 한다. {@code @Component} 로 두면 실제 구현체와
 * 함께 올라와 어느 쪽이 물릴지가 불분명해진다.
 */
public class StubAiTurnClient implements AiTurnClient {

    static final String STUB_STATE = "{\"stub\":true}";

    /** AI 계약의 SOCRATES 8축. 순서까지 실제와 같게 둔다. */
    private static final List<String> AXES = List.of(
            "site", "onset", "character", "radiation",
            "associated", "time_course", "exacerbating_relieving", "severity");

    /**
     * 물어볼 축의 순서. AI 계약의 8축 중 앞쪽 넷을 흉내 낸다.
     *
     * <p>실제 AI 는 환자 답변에서 이미 채워진 축을 건너뛰지만, 스텁은 그러지 않는다.
     */
    private static final List<String> QUESTIONS = List.of(
            "언제부터 그러셨어요? 정확하지 않아도 괜찮아요.",
            "어떨 때 더 아프세요?",
            "어떻게 아픈지 말씀해 주시겠어요? 쑤시는지, 뻐근한지처럼요.",
            "가장 심할 때를 10점이라고 하면 지금은 몇 점 정도인가요?");

    private static final String CLOSING = "말씀해 주셔서 감사해요. 정리해서 카드로 만들어 드릴게요.";

    @Override
    public AiTurnResult start(IntakeSession session) {
        String opening = session.getSiteText() == null
                ? "어디가 어떻게 불편해서 오셨는지 편하게 말씀해 주세요."
                : session.getSiteText() + "이(가) 불편하시군요. " + QUESTIONS.get(0);

        return new AiTurnResult(opening, false, null, STUB_STATE, card(session, null));
    }

    @Override
    public AiTurnResult turn(IntakeSession session, String utterance) {
        // 이 발화는 서비스가 이미 저장한 뒤에 들어온다. 여기서 또 세면 두 번 센다.
        long answered = session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.USER)
                .count();

        String card = card(session, utterance);
        if (answered >= QUESTIONS.size()) {
            return new AiTurnResult(CLOSING, true, "complete", STUB_STATE, card);
        }
        return new AiTurnResult(QUESTIONS.get((int) answered), false, null, STUB_STATE, card);
    }

    /**
     * 실제 계약과 <b>모양이 같은</b> 카드를 만든다.
     *
     * <p>값은 가짜지만 구조는 진짜여야 한다. 축 8개를 {@code not_asked} 로 깔고 발화가 있으면
     * {@code site} 만 채운다 — 1턴째 카드가 대부분 {@code not_asked} 인 실제 동작을 그대로
     * 흉내 내야, 3값으로 검증하다 터지는 것 같은 문제가 스텁에서도 드러난다.
     */
    private String card(IntakeSession session, String utterance) {
        String complaint = utterance == null ? session.getSiteText() : utterance;

        StringBuilder axes = new StringBuilder();
        for (String axis : AXES) {
            if (!axes.isEmpty()) {
                axes.append(',');
            }
            boolean filled = "site".equals(axis) && complaint != null;
            axes.append("\"").append(axis).append("\":{\"status\":\"")
                    .append(filled ? "filled" : "not_asked")
                    .append("\",\"value\":").append(filled ? quote(complaint) : "null")
                    .append(",\"evidence\":").append(filled ? "[" + quote(complaint) + "]" : "[]")
                    .append("}");
        }

        return "{\"card_type\":\"previsit\",\"chief_complaint\":" + quote(complaint)
                + ",\"axes\":{" + axes + "}"
                + ",\"red_flags\":[],\"patient_notes\":[]"
                + ",\"minimally_complete\":false,\"completeness\":" + (complaint == null ? "0.0" : "0.125")
                + ",\"department_guidance\":null"
                + ",\"provenance\":{\"prompt_version\":\"stub\",\"model_id\":\"stub\",\"ontology_snapshot\":null}}";
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
