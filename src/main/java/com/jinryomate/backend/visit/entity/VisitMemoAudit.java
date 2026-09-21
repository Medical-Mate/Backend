package com.jinryomate.backend.visit.entity;

import com.jinryomate.backend.auth.entity.User;
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
 * 메모 분류 한 번의 감사 기록.
 *
 * <p>AI 트랙이 <b>회귀 eval 케이스</b>를 채우려고 실사용 메모를 받습니다
 * (Medical-Mate/AI#113). 문답 쪽 {@link com.jinryomate.backend.intake.entity.IntakeTurnAudit}
 * 와 같은 목적이고 같은 날 함께 지웁니다.
 *
 * <p><b>열어보지 않습니다.</b> 메모 원문 · 나눈 문장 · 붙은 라벨 · 모델 원본 출력 ·
 * 버린 것 · {@code usage} 가 들어 있고 구조는 AI 계약이 정합니다. {@code state}·
 * {@code card} 를 문자열로 들고 다니는 것과 같은 이유로, 우리가 읽기 시작하면 저쪽
 * 변경이 우리를 깨뜨립니다.
 *
 * <p><b>회원 직속입니다.</b> 문답 감사 기록은 세션의 자식이라 cascade 에 얹혀 있는데
 * 이것은 부모가 없습니다 — {@code POST /api/visits/classify} 는 미리보기라 그 시점에
 * {@link VisitRecord} 행이 아직 없습니다. 그래서 탈퇴 삭제를
 * {@code AuthService.deleteAllData} 가 직접 하고 {@code WithdrawCascadeTest} 가 지킵니다.
 *
 * <p><b>한 메모가 여러 행이 됩니다.</b> 앱이 문장 나누기 · 모델 분류 · 라벨 되보내기로
 * {@code classify} 를 두 번 이상 부릅니다. {@link #source} 로 구별하고 <b>거르는 것은
 * 내보낼 때</b> 합니다 — {@code client} 가 곧 환자가 고친 정답 라벨이라, 저장 시점에
 * 버리면 eval 에 제일 값어치 있는 쪽이 사라집니다.
 *
 * <p><b>한시입니다.</b> 심사가 끝나고 내보내기 파일을 넘긴 뒤에는 테이블째 지웁니다.
 * 안 지우면 메모가 영영 남습니다.
 */
@Entity
@Table(name = "visit_memo_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitMemoAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 누가 라벨을 붙였나. {@code server} 모델 · {@code client} 환자가 고친 것 ·
     * {@code none} 문장만 나눔.
     *
     * <p>계약 최상위 필드라 읽습니다. {@code audit} 안을 여는 것이 아닙니다.
     * 계약이 바뀌어 안 오면 {@code null} 이고, 그때도 저장은 됩니다.
     */
    @Column(length = 16)
    private String source;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String audit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private VisitMemoAudit(User user, String source, String audit) {
        this.user = user;
        this.source = source;
        this.audit = audit;
        this.createdAt = Instant.now();
    }

    public static VisitMemoAudit of(User user, String source, String audit) {
        return new VisitMemoAudit(user, source, audit);
    }
}
