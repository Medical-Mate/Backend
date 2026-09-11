package com.jinryomate.backend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.ai.dto.PatientProfile;
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
     * @param utterance      환자가 말한 것. 음성이었어도 텍스트로 변환된 뒤에 온다
     * @param extraction     폰이 만든 추출 결과. 온디바이스 프로필에서만 온다.
     *                       <b>열어보지 않고 그대로 넘긴다.</b> 없으면 {@code null}
     * @param extractionMeta 폰 모델·프롬프트 버전. {@code extraction} 과 함께 온다
     */
    AiTurnResult turn(IntakeSession session, String utterance, JsonNode extraction, JsonNode extractionMeta);

    /**
     * 문답이 끝난 뒤 질문 후보를 받아온다. 화면 {@code 1i} 가 쓴다.
     *
     * <p><b>종료 뒤에 따로 부른다.</b> 계약은 종료 턴 요청에 얹으라고 하지만, 어느 턴이
     * 마지막인지는 <b>응답의 {@code ended} 를 봐야 알 수 있다</b> — 보내는 시점에는 모른다.
     * 매 턴 켜 두면 건강정보를 매 턴 실어 보내게 되어, AI 쪽이 422 로 막으려던 그 상태가
     * 그대로 생긴다.
     *
     * <p>종료 뒤 호출이 실제로 후보를 만들어 주는 것은 확인했다. 발화 없이 상태만 보낸다.
     *
     * <p><b>Bedrock 호출이 한 번 더 든다.</b> 문답 하나에 한 번이고, 그 비용은 우리 크레딧에서
     * 나간다 — 그래서 켜는 시점을 우리가 쥔다.
     *
     * @param profile 건강정보. 없으면 {@code null} — 그때는 카드 축만으로 질문을 만든다
     */
    AiTurnResult requestQuestionCandidates(IntakeSession session, PatientProfile profile);
}
