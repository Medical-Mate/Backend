package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, Long> {

    void deleteAllByUser(User user);
}
