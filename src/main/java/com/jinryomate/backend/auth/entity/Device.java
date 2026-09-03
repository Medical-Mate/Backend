package com.jinryomate.backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 푸시 알림을 받을 기기.
 *
 * <p>로그인 수단이 아니다. S6 "오늘 검사 결과 나오는 날" 알림을 보내려면
 * 기기별 푸시 토큰이 필요해서 둔다. user 1 : device N.
 */
@Entity
@Table(name = "devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    public enum Platform { ANDROID, IOS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 512)
    private String pushToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Platform platform;

    @Column(nullable = false)
    private Instant lastSeenAt;

    private Device(User user, String pushToken, Platform platform) {
        this.user = user;
        this.pushToken = pushToken;
        this.platform = platform;
        this.lastSeenAt = Instant.now();
    }

    public static Device of(User user, String pushToken, Platform platform) {
        return new Device(user, pushToken, platform);
    }

    /** 같은 기기가 다른 계정으로 로그인했을 수 있으므로 소유자도 갱신한다. */
    public void refresh(User user, Platform platform) {
        this.user = user;
        this.platform = platform;
        this.lastSeenAt = Instant.now();
    }
}
