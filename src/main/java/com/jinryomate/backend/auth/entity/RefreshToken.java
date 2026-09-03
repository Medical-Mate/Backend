package com.jinryomate.backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 폐기 가능한 refresh 토큰.
 *
 * <p>원문이 아니라 <b>해시</b>를 저장한다. DB가 유출돼도 그것만으로 로그인되지 않게 한다.
 *
 * <p>저장하는 이유는 탈퇴·로그아웃 시 즉시 무효화하기 위해서다.
 * {@code DELETE /me}에서 카드·기록까지 지우기로 해놓고 토큰만 살아 있으면 앞뒤가 맞지 않는다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(User user, String tokenHash, Instant expiresAt) {
        return new RefreshToken(user, tokenHash, expiresAt);
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
