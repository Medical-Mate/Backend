package com.jinryomate.backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 카카오 회원번호로만 식별한다.
 *
 * <p>이름·나이·성별은 여기 두지 않는다. 온보딩에서 직접 입력받아 health_profile에 저장한다.
 * 카카오에서 받는 값은 회원번호 하나뿐이다.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long kakaoId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private User(Long kakaoId) {
        this.kakaoId = kakaoId;
    }

    public static User ofKakao(Long kakaoId) {
        return new User(kakaoId);
    }
}
