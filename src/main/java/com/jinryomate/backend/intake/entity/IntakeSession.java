package com.jinryomate.backend.intake.entity;

import com.jinryomate.backend.auth.entity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 증상 문답 세션. 화면 S1.5 · S2.
 *
 * <p>부위 짚기로 시작해 문답을 이어간다. 시작 부위와 진행도를 세션에 들고 있는 이유는
 * 앱을 껐다 켜도 이어져야 하기 때문이다.
 */
@Entity
@Table(name = "intake_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeSession {

    public enum Status {
        /** 문답 진행 중. */
        IN_PROGRESS,
        /** 문답이 끝나 카드를 만들 수 있다. */
        COMPLETED,
        /** 환자가 중간에 그만뒀다. */
        ABANDONED
    }

    /** 와이어프레임의 "증상 문답 · 2/6" 기준. */
    public static final int DEFAULT_TOTAL_STEPS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.IN_PROGRESS;

    /**
     * S1.5에서 짚은 부위 코드.
     *
     * <p>부위 마스터가 아직 없어 지금은 형식만 본다. 마스터가 생기면 대조를 붙인다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "intake_session_sites", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "site_code", nullable = false, length = 64)
    private List<String> siteCodes = new ArrayList<>();

    /** 사람이 읽는 표현. 예: {@code 손가락 관절(오른손)}. 문답 첫 문장에 그대로 들어간다. */
    @Column(length = 100)
    private String siteText;

    @Column(nullable = false)
    private int progressCurrent = 0;

    @Column(nullable = false)
    private int progressTotal = DEFAULT_TOTAL_STEPS;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<IntakeMessage> messages = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    private IntakeSession(User user, List<String> siteCodes, String siteText) {
        this.user = user;
        if (siteCodes != null) {
            this.siteCodes.addAll(siteCodes);
        }
        this.siteText = siteText;
    }

    /** S1.5에서 부위를 짚고 시작한다. 부위를 건너뛰면 비어 있을 수 있다. */
    public static IntakeSession start(User user, List<String> siteCodes, String siteText) {
        return new IntakeSession(user, siteCodes, siteText);
    }

    /** 다음 메시지 순번. 카드의 evidence 가 이 번호를 참조한다. */
    public int nextSeq() {
        return messages.size() + 1;
    }

    public void addMessage(IntakeMessage message) {
        messages.add(message);
        if (message.getRole() == IntakeMessage.Role.USER) {
            this.progressCurrent = Math.min(progressCurrent + 1, progressTotal);
        }
    }

    public void complete() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
