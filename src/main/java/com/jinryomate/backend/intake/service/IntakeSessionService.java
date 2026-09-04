package com.jinryomate.backend.intake.service;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.dto.IntakeDtos.SessionResponse;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.entity.IntakeSession;
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

        IntakeSession session = sessionRepository.save(IntakeSession.start(
                user,
                request.siteCodes() == null ? List.of() : request.siteCodes(),
                request.siteText()));

        // 부위·증상은 민감정보라 값을 로그에 남기지 않는다.
        log.info("문답 세션 시작 userId={} sessionId={}", userId, session.getId());
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
