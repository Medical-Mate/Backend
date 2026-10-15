package com.jinryomate.backend.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalTime;
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

    /** 시간 미정 일정의 알림 기본 시각. V27 의 컬럼 기본값과 같아야 한다. */
    public static final LocalTime DEFAULT_REMINDER_TIME = LocalTime.of(19, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long kakaoId;

    /**
     * 진료 전 알림. 화면 {@code 1s-1} 의 토글.
     *
     * <p>시각을 적은 일정은 진료 24시간 전, 시간 미정 일정은 전날 {@link #visitReminderTime}
     * 에 울린다. <b>예약은 앱이 로컬로 한다</b> — 서버는 값만 들고 있다.
     *
     * <p><b>알림 설정만 서버에 둔다.</b> 지금 알림을 예약하는 것은 앱이지만, 알림을 받을지는
     * 기기 취향이 아니라 그 사람의 선택이다. 기기에만 있으면 기기를 바꾸거나 앱을 다시 깐
     * 뒤 "안 받겠다"고 한 사람에게 알림이 다시 가기 시작한다.
     *
     * <p>같은 화면의 "브리핑 카드 자동 저장"과 "진료실 화면 밝기 최대", "헤이 메이트로
     * 부르기"는 다르다. 이 기기에서 어떻게 보일지, 이 기기에 무엇을 받을지의 문제라 앱의
     * {@code DataStore} 에 둔다.
     *
     * <p>기본값은 켜짐이다. 진료를 놓치지 않게 하는 것이 이 앱의 목적이라, 명시적으로
     * 끄기 전까지는 알린다.
     */
    @Column(nullable = false)
    private boolean visitReminderEnabled = true;

    /**
     * 진료 후 기록 알림. 시각을 적은 일정은 진료 2시간 뒤, 시간 미정 일정은 당일
     * {@link #recordReminderTime} 에 울린다. 기본값은 진료 전 알림과 같은 이유로 켜짐이다.
     */
    @Column(nullable = false)
    private boolean recordReminderEnabled = true;

    /**
     * 시간 미정 일정의 전날 알림 시각. 시간대 없이 한국 시각으로 읽는다 — 일정의
     * {@code scheduledTime} 과 같다. 시각을 적은 일정은 규칙이 고정이라 이 값을 안 쓴다.
     */
    @Column(nullable = false)
    private LocalTime visitReminderTime = DEFAULT_REMINDER_TIME;

    /** 시간 미정 일정의 당일 기록 알림 시각. 읽는 법은 {@link #visitReminderTime} 과 같다. */
    @Column(nullable = false)
    private LocalTime recordReminderTime = DEFAULT_REMINDER_TIME;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private User(Long kakaoId) {
        this.kakaoId = kakaoId;
    }

    public static User ofKakao(Long kakaoId) {
        return new User(kakaoId);
    }

    /**
     * 보낸 것만 바꾼다. {@code null} 은 "그대로 둠" 이다.
     *
     * <p>시각 하나만 고칠 때 토글까지 같이 보내게 하지 않으려는 것이다. 빠진 값을 기본값으로
     * 채우면 보내지 않은 설정이 말없이 되돌아간다. 일정 {@code PATCH} 와 같은 모양이다.
     */
    public void applyReminderSettings(Boolean visitEnabled, Boolean recordEnabled,
                                      LocalTime visitTime, LocalTime recordTime) {
        if (visitEnabled != null) {
            this.visitReminderEnabled = visitEnabled;
        }
        if (recordEnabled != null) {
            this.recordReminderEnabled = recordEnabled;
        }
        if (visitTime != null) {
            this.visitReminderTime = visitTime;
        }
        if (recordTime != null) {
            this.recordReminderTime = recordTime;
        }
    }
}
