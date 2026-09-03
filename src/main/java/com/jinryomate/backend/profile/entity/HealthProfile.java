package com.jinryomate.backend.profile.entity;

import com.jinryomate.backend.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 온보딩에서 받는 환자 정보. 화면 S1.
 *
 * <p>여기 담긴 값이 브리핑 카드 헤더({@code 김○○ · 32세 여})와 본문(복용약·알레르기)에 그대로 들어간다.
 *
 * <p>나이 대신 <b>출생연도</b>를 저장한다. 나이를 저장하면 해가 바뀔 때 낡고, 카카오도 출생연도를 준다.
 */
@Entity
@Table(name = "health_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class HealthProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // --- 카드 헤더에 찍히는 값. 카카오에서 받거나 온보딩에서 입력한다 ---

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldSource nameSource;

    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldSource birthYearSource;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldSource sexSource;

    // --- 온보딩에서만 받는 값 ---

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "health_profile_medications", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "item", nullable = false)
    private List<String> medications = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldStatus medicationsStatus = FieldStatus.UNKNOWN;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "health_profile_conditions", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "item", nullable = false)
    private List<String> conditions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldStatus conditionsStatus = FieldStatus.UNKNOWN;

    private String allergies;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private FieldStatus allergiesStatus = FieldStatus.UNKNOWN;

    /** 온보딩을 실제로 마친 시각. 카카오 값이 채워진 것만으로는 완료가 아니다. */
    private Instant completedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private HealthProfile(User user) {
        this.user = user;
    }

    public static HealthProfile emptyFor(User user) {
        return new HealthProfile(user);
    }

    /**
     * 카카오에서 받은 값을 채운다.
     *
     * <p>사용자가 직접 고친 필드({@link FieldSource#SELF_INPUT})는 건드리지 않는다.
     * 값이 없는 항목(동의 거부)은 그냥 넘어간다.
     */
    public void applyKakaoValues(String kakaoName, Sex kakaoSex, Integer kakaoBirthYear) {
        if (kakaoName != null && nameSource != FieldSource.SELF_INPUT) {
            this.name = kakaoName;
            this.nameSource = FieldSource.KAKAO;
        }
        if (kakaoSex != null && sexSource != FieldSource.SELF_INPUT) {
            this.sex = kakaoSex;
            this.sexSource = FieldSource.KAKAO;
        }
        if (kakaoBirthYear != null && birthYearSource != FieldSource.SELF_INPUT) {
            this.birthYear = kakaoBirthYear;
            this.birthYearSource = FieldSource.KAKAO;
        }
    }

    /** 온보딩 저장. 여기서 들어온 값은 전부 사용자가 직접 넣은 것으로 표시한다. */
    public void completeOnboarding(String name,
                                   Integer birthYear,
                                   Sex sex,
                                   FieldStatus medicationsStatus, List<String> medications,
                                   FieldStatus conditionsStatus, List<String> conditions,
                                   FieldStatus allergiesStatus, String allergies) {
        this.name = name;
        this.nameSource = FieldSource.SELF_INPUT;
        this.birthYear = birthYear;
        this.birthYearSource = FieldSource.SELF_INPUT;
        this.sex = sex;
        this.sexSource = FieldSource.SELF_INPUT;

        this.medicationsStatus = medicationsStatus;
        this.medications.clear();
        if (medicationsStatus == FieldStatus.KNOWN && medications != null) {
            this.medications.addAll(medications);
        }

        this.conditionsStatus = conditionsStatus;
        this.conditions.clear();
        if (conditionsStatus == FieldStatus.KNOWN && conditions != null) {
            this.conditions.addAll(conditions);
        }

        this.allergiesStatus = allergiesStatus;
        this.allergies = (allergiesStatus == FieldStatus.KNOWN) ? allergies : null;

        this.completedAt = Instant.now();
    }

    /** 카드에 찍히는 만 나이. 생일 전이면 한 살 적게 나올 수 있으나 출생연도만으로는 그 이상 알 수 없다. */
    public Integer age() {
        return birthYear == null ? null : Year.now().getValue() - birthYear;
    }

    public boolean isOnboardingCompleted() {
        return completedAt != null;
    }

    /**
     * 문답을 시작할 수 있는지.
     *
     * <p>나이·성별은 카드 헤더에 반드시 찍히므로 없으면 카드가 성립하지 않는다.
     * 복용약·기저질환·알레르기는 없어도 {@code unknown}으로 표시되므로 막지 않는다.
     */
    public boolean canStartIntake() {
        return birthYear != null && sex != null && sex != Sex.UNSPECIFIED;
    }
}
