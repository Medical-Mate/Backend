package com.jinryomate.backend.visit.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.visit.entity.VisitRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRecordRepository extends JpaRepository<VisitRecord, Long> {

    Optional<VisitRecord> findByCardId(Long cardId);

    boolean existsByCardId(Long cardId);

    void deleteAllByUser(User user);
}
