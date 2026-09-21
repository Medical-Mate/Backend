package com.jinryomate.backend.intake.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 턴 하나의 감사 기록.
 *
 * <p>AI 트랙이 <b>회귀 eval 케이스</b>를 채우려고 실사용 발화를 받습니다
 * (Medical-Mate/AI#113). 지금까지 모델 품질을 자체 케이스 51개로만 봤고 실사용
 * 발화가 한 건도 없었습니다.
 *
 * <p><b>열어보지 않습니다.</b> 물은 축 · LLM 에 들어간 발화 · 모델 원본 추출 ·
 * 가드 통과분 · 버린 것 · {@code usage} 가 들어 있고 구조는 AI 계약이 정합니다.
 * {@code state}·{@code card} 를 문자열로 들고 다니는 것과 같은 이유로, 우리가 읽기
 * 시작하면 저쪽 변경이 우리를 깨뜨립니다.
 *
 * <p><b>세션의 자식입니다.</b> {@link IntakeSession} 의 cascade 에 얹혀 있어 탈퇴가
 * 세션을 지우면 이것도 함께 사라집니다 — {@code AuthService.deleteAllData} 에 줄을
 * 더할 필요가 없고 {@code WithdrawCascadeTest} 가 그대로 지킵니다.
 *
 * <p><b>한시입니다.</b> 심사가 끝나고 내보내기 파일을 넘긴 뒤에는 테이블째 지웁니다.
 * 안 지우면 발화가 영영 남습니다.
 */
@Entity
@Table(name = "intake_turn_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeTurnAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private IntakeSession session;

    /** 몇 번째 턴인가. {@code intake_messages} 와 같은 축이라 발화와 맞춰 볼 수 있다. */
    @Column(nullable = false)
    private int seq;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String audit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private IntakeTurnAudit(IntakeSession session, int seq, String audit) {
        this.session = session;
        this.seq = seq;
        this.audit = audit;
        this.createdAt = Instant.now();
    }

    public static IntakeTurnAudit of(IntakeSession session, int seq, String audit) {
        return new IntakeTurnAudit(session, seq, audit);
    }
}
