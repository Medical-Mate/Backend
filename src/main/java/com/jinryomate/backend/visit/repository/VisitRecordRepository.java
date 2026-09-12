package com.jinryomate.backend.visit.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.visit.entity.VisitRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRecordRepository extends JpaRepository<VisitRecord, Long> {

    Optional<VisitRecord> findByCardId(Long cardId);

    boolean existsByCardId(Long cardId);

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
