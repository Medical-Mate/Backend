package com.jinryomate.backend.card.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingCardRepository extends JpaRepository<BriefingCard, Long> {

    void deleteAllByUser(User user);
}
