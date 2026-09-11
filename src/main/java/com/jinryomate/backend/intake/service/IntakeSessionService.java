package com.jinryomate.backend.intake.service;

import com.jinryomate.backend.ai.client.AiTurnClient;
import com.jinryomate.backend.ai.dto.AiTurnResult;
import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionsRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SendMessageRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.SessionResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.SeverityRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.dto.IntakeDtos.TurnResponse;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.entity.Side;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
import com.jinryomate.backend.profile.service.HealthProfileService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeSessionService {

    private final IntakeSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final HealthProfileService healthProfileService;
    private final AiTurnClient aiTurnClient;
    private final BodyMap bodyMap;

    /**
     * 문답 세션을 시작한다.
     *
     * <p><b>나이·성별이 없으면 시작할 수 없다.</b> 이 둘은 의사용 카드 헤더에 반드시 찍혀서,
     * 없으면 문답을 다 해도 카드가 성립하지 않는다. 복용약·기저질환·알레르기는 없어도
     * {@code unknown}으로 표시되므로 막지 않는다.
     */
    @Transactional
    public SessionResponse start(Long userId, StartSessionRequest request) {
        if (!healthProfileService.canStartIntake(userId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "나이와 성별을 먼저 입력해주세요. 카드에 들어가는 정보입니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        validateSite(request.siteNodeId(), request.side());

        IntakeSession session = sessionRepository.save(IntakeSession.start(
                user, request.siteNodeId(), request.side(), request.siteText()));

        // 첫 질문을 여기서 받아 대화에 넣는다. 앱이 세션을 만든 뒤 또 호출하지 않아도
        // 바로 화면을 그릴 수 있다. AI 계약상 이 호출은 LLM 을 쓰지 않는다.
        AiTurnResult opening = aiTurnClient.start(session);
        session.rememberState(opening.state());
        session.rememberCard(opening.card());
        session.addMessage(IntakeMessage.fromAi(session, opening.reply()));

        // 부위·증상은 민감정보라 값을 로그에 남기지 않는다.
        log.info("문답 세션 시작 userId={} sessionId={}", userId, session.getId());
        return SessionResponse.from(session);
    }

    /**
     * 환자 발화를 넘기고 다음 질문을 받는다.
     *
     * <p>대화 두 줄(환자 발화 · AI 응답)이 한 번에 쌓인다. 둘을 따로 저장하면 중간에
     * 실패했을 때 답변만 남고 질문이 없는 대화가 된다.
     *
     * <p><b>이미 끝난 문답에도 보낼 수 있다.</b> AI 계약이 "종료 뒤에 또 턴을 보내면
     * 마무리 문장만 돌아오고 상태는 바뀌지 않는다"고 정했다. 오류로 막으면 앱이
     * 종료 시점을 정확히 알아야 하는데, 네트워크가 끊긴 사이에 끝났을 수도 있다.
     */
    @Transactional
    public TurnResponse sendMessage(Long userId, Long sessionId, SendMessageRequest request) {
        IntakeSession session = findOwned(userId, sessionId);

        if (session.isCompleted()) {
            // 상태를 바꾸지 않고 마지막 AI 문장만 돌려준다.
            return TurnResponse.of(session, lastAiReply(session), true);
        }

        session.addMessage(IntakeMessage.fromUser(session, request.text(), request.inputMethod()));

        AiTurnResult result = aiTurnClient.turn(session, request.text());
        session.rememberState(result.state());
        session.rememberCard(result.card());
        session.addMessage(IntakeMessage.fromAi(session, result.reply()));

        if (result.ended()) {
            session.complete(result.endReason());
        }

        // 발화 내용은 증상 텍스트라 로그에 남기지 않는다. 길이만 남긴다.
        log.info("문답 턴 sessionId={} chars={} ended={} endReason={}",
                sessionId, request.text().length(), result.ended(), result.endReason());

        return TurnResponse.of(session, result.reply(), result.ended());
    }

    /**
     * 부위를 여기서 먼저 거른다.
     *
     * <p>AI 도 같은 검증을 하지만 그대로 넘기면 <b>문답을 시작한 뒤에</b> 422 가 돌아온다.
     * 앱은 이미 화면을 넘긴 뒤라 되돌리기가 어렵다.
     *
     * <p>부위를 건너뛰는 경로가 있어 {@code null} 은 통과시킨다. 다만 <b>부위 없이 좌우만</b>
     * 보내는 것은 막는다 — 무엇의 좌우인지 알 수 없다.
     */
    private void validateSite(String siteNodeId, Side side) {
        if (siteNodeId == null || siteNodeId.isBlank()) {
            if (side != null) {
                throw new ApiException(ErrorCode.INVALID_REQUEST, "부위를 함께 보내주세요.");
            }
            return;
        }

        BodyMap.Node node = bodyMap.get(siteNodeId);
        if (node == null) {
            log.warn("부위 마스터에 없는 코드 siteNodeId={} snapshot={}", siteNodeId, bodyMap.getSnapshot());
            throw new ApiException(ErrorCode.INVALID_REQUEST, "알 수 없는 부위입니다.");
        }

        if (side != null && !node.lateralized()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    node.label() + "에는 좌우가 없습니다.");
        }
    }

    private String lastAiReply(IntakeSession session) {
        return session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.AI)
                .reduce((first, second) -> second)
                .map(IntakeMessage::getText)
                .orElse("이미 끝난 문답입니다.");
    }

    /**
     * 통증 강도를 기록한다. 화면 3단계.
     *
     * <p><b>끝난 세션에도 쓸 수 있다.</b> 3단계는 문답이 끝난 뒤 화면이라
     * {@code COMPLETED} 라고 막으면 정상 흐름이 막힌다.
     */
    @Transactional
    public SessionResponse recordSeverity(Long userId, Long sessionId, SeverityRequest request) {
        IntakeSession session = findOwned(userId, sessionId);
        session.recordSeverity(request.level(), request.label());

        // 강도 값 자체는 증상 정보라 라벨을 로그에 남기지 않는다. 숫자만 남긴다.
        log.info("통증 강도 기록 sessionId={} level={}", sessionId, request.level());
        return SessionResponse.from(session);
    }

    /**
     * 의사에게 물어볼 것을 통째로 교체한다. 화면 4단계.
     *
     * <p>추가·편집·삭제·순서변경이 전부 이 한 번으로 처리된다. 빈 배열이면 전부 지운다.
     */
    @Transactional
    public SessionResponse replaceQuestions(Long userId, Long sessionId, QuestionsRequest request) {
        IntakeSession session = findOwned(userId, sessionId);
        session.replaceQuestions(request.questions());

        // 질문 내용은 증상을 유추할 수 있어 로그에 남기지 않는다. 개수만 남긴다.
        log.info("물어볼 것 저장 sessionId={} count={}", sessionId, request.questions().size());
        return SessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(Long userId, Long sessionId) {
        return SessionResponse.from(findOwned(userId, sessionId));
    }

    /** 남의 세션은 존재 자체를 알려주지 않는다. 없는 것과 같은 응답을 준다. */
    @Transactional(readOnly = true)
    public IntakeSession findOwned(Long userId, Long sessionId) {
        IntakeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "문답을 찾을 수 없습니다."));
        if (!session.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "문답을 찾을 수 없습니다.");
        }
        return session;
    }
}
