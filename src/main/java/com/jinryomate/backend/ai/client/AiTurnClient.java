package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.intake.entity.IntakeSession;

/**
 * 증상 문답의 다음 질문을 받아오는 AI 서비스 호출.
 *
 * <p>AI 서버는 무상태다. 턴마다 {@code state} 와 환자 발화를 전부 넘기고, 돌아온
 * {@code state} 로 덮어쓴다. 저장은 백엔드가 전담한다 — 환자 데이터가 두 군데로
 * 쪼개지면 개인정보 처리 범위가 두 배가 된다.
 *
 * <p>지금은 {@link StubAiTurnClient} 가 구현한다. <b>HMAC 서명 규격이 오면</b>
 * 실제 구현체로 갈아끼우고 이 인터페이스는 그대로 둔다.
 */
public interface AiTurnClient {

    /**
     * 문답을 시작한다. 첫 질문과 초기 {@code state} 를 받는다.
     *
     * <p>AI 계약상 이 호출은 LLM 을 쓰지 않는다.
     */
    AiTurnResult start(IntakeSession session);

    /**
     * 환자 발화를 넘기고 다음 질문을 받는다.
     *
     * @param session   진행 중인 세션. 보관해둔 {@code state} 를 여기서 꺼낸다
     * @param utterance 환자가 말한 것. 음성이었어도 텍스트로 변환된 뒤에 온다
     */
    AiTurnResult turn(IntakeSession session, String utterance);
}
