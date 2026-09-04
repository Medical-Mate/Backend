package com.jinryomate.backend.handoff.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.handoff.entity.ShareLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByToken(String token);

    List<ShareLink> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteAllByUser(User user);
}
