package com.jinryomate.backend.visit.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.visit.entity.VisitRecord;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRecordRepository extends JpaRepository<VisitRecord, Long> {

    /**
     * 이 문답에서 나온 카드들에 붙은 기록 전부. 최근 진료일 순.
     *
     * <p><b>카드 행이 아니라 문답으로 찾는다.</b> 환자가 재방문 전에 카드를 고치면 버전이
     * 올라가서, 첫 기록과 두 번째 기록이 서로 다른 행에 붙는다. 행으로 찾으면 한쪽만
     * 나오고 시안 {@code 1j-3-R} 의 "진료 2회" 가 1회로 보인다.
     */
    @Query("""
            select v from VisitRecord v
            where v.card.session.id = :sessionId
            order by v.visitedOn desc, v.id desc
            """)
    List<VisitRecord> findAllBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 기록 탭의 목록.
     *
     * <p>같은 날 진료가 둘이면 진료일만으로는 순서가 흔들린다. id 를 두 번째 기준으로 둬서
     * 목록 순서가 매 요청 같도록 한다.
     */
    List<VisitRecord> findAllByUserIdOrderByVisitedOnDescIdDesc(Long userId);

    /**
     * 카드를 지울 때 그 카드를 가리키던 기록을 찾는다.
     *
     * <p><b>기록은 지우지 않고 연결만 끊는다.</b> 카드는 준비물이고 기록은 진료에서 들은
     * 것이라, 준비물을 지웠다고 사라질 값이 아니다.
     */
    List<VisitRecord> findAllByCardIdIn(java.util.Collection<Long> cardIds);

    void deleteAllByUser(User user);
}
