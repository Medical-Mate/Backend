package com.jinryomate.backend.card.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingCardRepository extends JpaRepository<BriefingCard, Long> {

    /**
     * 기록 탭의 카드 목록.
     *
     * <p>작성 시각 내림차순. 같은 시각이 겹칠 수 있어 id 를 두 번째 기준으로 둔다.
     */
    List<BriefingCard> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    void deleteAllByUser(User user);
}
