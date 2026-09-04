package com.jinryomate.backend.intake.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문답 대화 한 줄.
 *
 * <p>{@code seq}는 카드의 {@code evidence}가 참조하는 번호다. "이 문장이 어느 답변에서
 * 나왔는지"를 남겨두면 재방문 브리핑에서 "그 사이 달라진 것"을 계산할 수 있다.
 *
 * <p>본문은 증상 텍스트라 민감정보다. 로그에 남기지 않는다.
 */
@Entity
@Table(name = "intake_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeMessage {

    public enum Role { AI, USER }

    public enum InputMethod {
        TEXT,
        /** 음성 입력. 녹음 자체는 저장하지 않고 변환된 텍스트만 남긴다. */
        STT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private IntakeSession session;

    @Column(nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Role role;

    @Column(nullable = false, length = 2000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private InputMethod inputMethod;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private IntakeMessage(IntakeSession session, int seq, Role role, String text, InputMethod inputMethod) {
        this.session = session;
        this.seq = seq;
        this.role = role;
        this.text = text;
        this.inputMethod = inputMethod;
    }

    public static IntakeMessage fromAi(IntakeSession session, String text) {
        return new IntakeMessage(session, session.nextSeq(), Role.AI, text, null);
    }

    public static IntakeMessage fromUser(IntakeSession session, String text, InputMethod inputMethod) {
        return new IntakeMessage(session, session.nextSeq(), Role.USER, text,
                inputMethod == null ? InputMethod.TEXT : inputMethod);
    }
}
