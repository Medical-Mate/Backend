package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, Long> {

    /**
     * 홈의 "이어서 하기" 후보. 가장 최근에 시작한 세션 하나다({@code status} 는 빼고 볼 상태).
     *
     * <p><b>최근 것 하나만 본다(#171).</b> 새 문답을 시작했으면 옛 문답은 놓은 것으로 본다.
     * 새 문답을 시작할 때 이전 것을 {@code ABANDONED} 로 돌리지만(#174), 그 전에 쌓인 세션은
     * 그대로라 조건에 맞는 것을 거슬러 찾으면 카드를 만든 직후 몇 주 전 세션이 되살아난다.
     *
     * <p>같은 시각에 둘이 시작됐으면 나중에 만든 쪽(id 가 큰 쪽)이다.
     */
    Optional<IntakeSession> findFirstByUserIdAndStatusNotOrderByStartedAtDescIdDesc(
            Long userId, IntakeSession.Status status);

    /**
     * 카드까지 가지 않은 문답. 새 문답을 시작할 때 이것들을 {@code ABANDONED} 로 돌린다(#174).
     *
     * <p><b>카드 저장소를 문답 서비스에 들이지 않으려고 여기 쿼리로 둔다.</b> 참조는 지금 카드 →
     * 문답 한쪽뿐이다. 문답 서비스가 카드 서비스를 부르면 둘이 서로를 부르게 된다.
     *
     * <p>카드를 만들면 세션이 {@code COMPLETED} 가 되므로, 카드 없는 세션은 진행 중이거나
     * 끝났지만 질문 고르기에서 멈춘 것이다.
     */
    @Query("""
            select s from IntakeSession s
            where s.user.id = :userId
              and s.status <> com.jinryomate.backend.intake.entity.IntakeSession.Status.ABANDONED
              and not exists (
                  select 1 from BriefingCard c where c.session = s)
            """)
    List<IntakeSession> findOpenWithoutCard(@Param("userId") Long userId);

    void deleteAllByUser(User user);
}
