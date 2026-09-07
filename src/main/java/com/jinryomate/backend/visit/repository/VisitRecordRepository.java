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

    void deleteAllByUser(User user);
}
