package com.jinryomate.backend.intake.repository;

import com.jinryomate.backend.intake.entity.IntakeMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntakeMessageRepository extends JpaRepository<IntakeMessage, Long> {
}
