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

    /**
     * 진료 하루 전 알림. 화면 {@code 1s-1} 의 토글.
     *
     * <p><b>이 토글만 서버에 둔다.</b> 지금 알림을 예약하는 것은 앱이지만, 알림을 받을지는
     * 기기 취향이 아니라 그 사람의 선택이다. 기기에만 있으면 기기를 바꾸거나 앱을 다시 깐
     * 뒤 "안 받겠다"고 한 사람에게 알림이 다시 가기 시작한다.
     *
     * <p>같은 화면의 "브리핑 카드 자동 저장"과 "진료실 화면 밝기 최대"는 다르다. 그 둘은
     * 이 기기에서 어떻게 보일지의 문제라 앱의 {@code DataStore} 에 둔다.
     *
     * <p>기본값은 켜짐이다. 진료를 놓치지 않게 하는 것이 이 앱의 목적이라, 명시적으로
     * 끄기 전까지는 알린다.
     */
    @Column(nullable = false)
    private boolean visitReminderEnabled = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private User(Long kakaoId) {
        this.kakaoId = kakaoId;
    }

    public static User ofKakao(Long kakaoId) {
        return new User(kakaoId);
    }

    public void changeVisitReminder(boolean enabled) {
        this.visitReminderEnabled = enabled;
    }
}
