package com.jinryomate.backend.handoff.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카드 공유 링크. 화면 S6.
 *
 * <p>인증 없이 열리므로 <b>짧은 만료가 유일한 방어선</b>이다. 토큰은 128비트 난수라
 * 추측할 수 없고, 잘못 보냈으면 {@link #revoke()} 로 즉시 끊는다.
 *
 * <p>JWT 무상태 대신 행을 남기는 이유가 그 폐기다. 증상·복용약이 담긴 링크라
 * 만료까지 기다리는 것 말고 다른 수단이 있어야 한다.
 */
@Entity
@Table(name = "share_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private BriefingCard card;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    private Instant viewedAt;

    @Column(nullable = false)
    private int viewCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private ShareLink(User user, BriefingCard card, String token, Instant expiresAt) {
        this.user = user;
        this.card = card;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    public static ShareLink issue(User user, BriefingCard card, String token, Instant expiresAt) {
        return new ShareLink(user, card, token, expiresAt);
    }

    public boolean isOpenable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void revoke() {
        if (revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    /** 열람 기록. 환자가 "누가 봤나"를 확인하는 근거다. */
    public void recordView() {
        this.viewedAt = Instant.now();
        this.viewCount++;
    }
}
