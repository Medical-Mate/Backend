package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, Long> {

    /**
     * 홈의 "이어서 하기".
     *
     * <p>가장 최근 것 하나만 쓴다. 여러 개가 보이면 세션을 언제 버릴지(ABANDONED 전이)
     * 규칙이 없다는 뜻이라, 그때는 정리 규칙을 먼저 손봐야 한다.
     */
    List<IntakeSession> findAllByUserIdAndStatusOrderByStartedAtDesc(
            Long userId, IntakeSession.Status status);

    void deleteAllByUser(User user);
}
