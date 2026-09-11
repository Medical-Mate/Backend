package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI 서비스에 실제로 붙기 전까지 쓰는 임시 구현.
 *
 * <p><b>질문이 고정이다.</b> 환자가 무엇을 답하든 다음 축을 순서대로 물을 뿐, 답변을
 * 읽지 않는다. 그래도 턴 처리 → 메시지 적재 → 진행도 → 종료 판정 → {@code state}
 * 왕복은 실제 경로로 돌아가므로, 앱이 S2 화면을 만들고 검증할 수 있다.
 *
 * <p>실제 구현체를 붙일 때 이 클래스를 지운다. 인터페이스는 그대로 둔다.
 *
 * <p><b>왜 아직 스텁인가</b> — AI 서버는 이미 떠 있지만 {@code MEDIMATE_HMAC_SECRET}
 * 서명 규격을 받지 못했다. 한쪽만 채우면 AI 가 우리 요청을 전부 거부한다.
 */
@Component
public class StubAiTurnClient implements AiTurnClient {

    static final String STUB_STATE = "{\"stub\":true}";

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

        return new AiTurnResult(opening, false, null, STUB_STATE);
    }

    @Override
    public AiTurnResult turn(IntakeSession session, String utterance) {
        // 이 발화는 서비스가 이미 저장한 뒤에 들어온다. 여기서 또 세면 두 번 센다.
        long answered = session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.USER)
                .count();

        if (answered >= QUESTIONS.size()) {
            return new AiTurnResult(CLOSING, true, "complete", STUB_STATE);
        }
        return new AiTurnResult(QUESTIONS.get((int) answered), false, null, STUB_STATE);
    }
}
