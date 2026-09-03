package com.jinryomate.backend.profile.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.profile.entity.HealthProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthProfileRepository extends JpaRepository<HealthProfile, Long> {

    Optional<HealthProfile> findByUserId(Long userId);

    void deleteByUser(User user);
}
