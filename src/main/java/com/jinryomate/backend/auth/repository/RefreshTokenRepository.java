package com.jinryomate.backend.auth.repository;

import com.jinryomate.backend.auth.entity.RefreshToken;
import com.jinryomate.backend.auth.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 로그아웃 시 이 사용자의 모든 토큰을 한 번에 막는다.
     *
     * <p>시각을 파라미터로 받는다. {@code CURRENT_TIMESTAMP}는 {@code Timestamp}로 평가돼
     * {@code Instant} 필드에 대입되지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user = :user and t.revokedAt is null")
    int revokeAllByUser(@Param("user") User user, @Param("now") Instant now);

    void deleteAllByUser(User user);
}
