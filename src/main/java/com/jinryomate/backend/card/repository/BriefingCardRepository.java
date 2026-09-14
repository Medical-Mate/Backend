package com.jinryomate.backend.card.repository;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingCardRepository extends JpaRepository<BriefingCard, Long> {

    /**
     * 기록 탭의 카드 목록.
     *
     * <p>작성 시각 내림차순. 같은 시각이 겹칠 수 있어 id 를 두 번째 기준으로 둔다.
     */
    List<BriefingCard> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /**
     * 한 문답의 최신 카드.
     *
     * <p>카드 생성을 여러 번 불러도 카드가 늘지 않게 하는 데 쓴다. 앱이 화면을 다시 그리거나
     * 네트워크가 끊겼다 이어져도 같은 카드가 나와야 한다.
     *
     * <p>환자가 확정된 카드를 고치면 버전이 올라간 새 행이 생기므로 버전 내림차순으로 찾는다.
     */
    Optional<BriefingCard> findFirstBySessionIdOrderByVersionDesc(Long sessionId);

    /** 한 문답에서 나온 카드 전부. 버전 체인이라 함께 다룬다. */
    List<BriefingCard> findAllBySessionIdOrderByVersionDesc(Long sessionId);

    /**
     * 이 카드를 이어받은 카드가 이미 있는지.
     *
     * <p>있으면 그 카드는 <b>이미 고친 카드</b>다. 또 고치면 같은 자리에서 가지가 하나 더
     * 나서 체인이 갈라지고, 그 가지에 넣은 편집은 어느 화면에도 안 나온다.
     */
    boolean existsByParentCardId(Long parentCardId);

    /**
     * 체인의 맨 끝. 옛 id 로 들어온 앱에게 "이걸로 다시 보내라"고 알려주는 데 쓴다.
     *
     * <p>버전만으로 정렬하지 않는다 — 이미 갈라진 데이터에는 같은 버전 번호가 둘 있다.
     * 그때는 나중에 만들어진 쪽(id 가 큰 쪽)이 최신이다.
     */
    Optional<BriefingCard> findFirstBySessionIdOrderByVersionDescIdDesc(Long sessionId);

    void deleteAllByUser(User user);
}
