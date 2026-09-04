package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.AiCardResult;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.profile.entity.HealthProfile;

/**
 * 대화를 브리핑 카드로 바꾸는 AI 서비스 호출.
 *
 * <p>AI 서비스는 무상태다. 호출할 때마다 필요한 컨텍스트를 전부 넘기고, 저장은 백엔드가 전담한다.
 * 환자 데이터가 두 군데로 쪼개지면 개인정보 처리 범위가 두 배가 된다.
 *
 * <p>지금은 {@link StubAiCardClient} 가 구현한다. AI 서비스 주소가 정해지면
 * 실제 구현체로 갈아끼우고 이 인터페이스는 그대로 둔다.
 */
public interface AiCardClient {

    AiCardResult generateCard(IntakeSession session, HealthProfile profile);
}
