package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, Long> {

    /**
     * 홈의 "이어서 하기" 후보. 가장 최근에 시작한 세션 하나다({@code status} 는 빼고 볼 상태).
     *
     * <p><b>최근 것 하나만 본다(#171).</b> 새 문답을 시작했으면 옛 문답은 놓은 것으로 본다 —
     * 세션을 버리는 전이({@code ABANDONED})가 없어서, 조건에 맞는 것을 거슬러 찾으면 카드를
     * 만든 직후 몇 주 전 세션이 되살아난다.
     *
     * <p>같은 시각에 둘이 시작됐으면 나중에 만든 쪽(id 가 큰 쪽)이다.
     */
    Optional<IntakeSession> findFirstByUserIdAndStatusNotOrderByStartedAtDescIdDesc(
            Long userId, IntakeSession.Status status);

    void deleteAllByUser(User user);
}
