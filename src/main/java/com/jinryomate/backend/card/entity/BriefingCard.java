package com.jinryomate.backend.card.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 브리핑 카드. 화면 S3.
 *
 * <p>환자 인적사항은 <b>만든 시점 값을 그대로 박아둔다</b>. 프로필을 참조하면 나중에
 * 이름을 고쳤을 때 이미 확정된 카드까지 바뀐다. 의사가 본 카드는 그대로 남아야 한다.
 *
 * <p>{@link CardStatus#CONFIRMED} 이후의 변경은 이 행을 고치지 않고
 * {@link #newVersion()} 으로 새 행을 만든다. {@code version} 과 {@code parentCard} 로 체인이 된다.
 */
@Entity
@Table(name = "briefing_cards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BriefingCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private IntakeSession session;

    // --- 버전 ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardStatus status = CardStatus.DRAFT;

    @Column(nullable = false)
    private int version = 1;

    /** 이 카드가 어느 카드를 이어받았는지. 첫 버전이면 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_card_id")
    private BriefingCard parentCard;

    // --- 환자 인적사항 스냅샷 ---

    @Column(length = 30)
    private String patientName;

    private Integer patientAge;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Sex patientSex;

    // --- 카드 본문 ---

    @Column(nullable = false, length = 20)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FieldStatus onsetStatus = FieldStatus.UNKNOWN;

    @Column(length = 80)
    private String onsetText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FieldStatus patternStatus = FieldStatus.UNKNOWN;

    @Column(length = 80)
    private String patternText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FieldStatus siteStatus = FieldStatus.UNKNOWN;

    @Column(length = 80)
    private String siteText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> siteCodes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FieldStatus medicationsStatus = FieldStatus.UNKNOWN;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Medication> medications = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FieldStatus allergiesStatus = FieldStatus.UNKNOWN;

    @Column(length = 80)
    private String allergiesText;

    /** 환자가 묻고 싶어 하는 것. 최대 3개. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> questions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private Department suggestedDepartment;

    /** 필드별 근거 메시지 순번. 예: {@code {"onset": [12, 14]}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, List<Integer>> evidence = new LinkedHashMap<>();

    // --- 추적성 ---

    /**
     * 어떤 AI 파이프라인이 만든 카드인지.
     *
     * <p>프롬프트는 AI 레포에서 관리하므로, 백엔드가 추적성을 확보하는 유일한 수단이다.
     */
    @Column(length = 64)
    private String pipelineVersion;

    /** 장애 시 AI 쪽 로그와 잇는 열쇠. */
    @Column(length = 64)
    private String aiRequestId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant confirmedAt;

    // --- 생성 ---

    private BriefingCard(User user, IntakeSession session) {
        this.user = user;
        this.session = session;
    }

    public static BriefingCard draft(User user, IntakeSession session) {
        return new BriefingCard(user, session);
    }

    public void applyPatientSnapshot(String name, Integer age, Sex sex) {
        this.patientName = name;
        this.patientAge = age;
        this.patientSex = sex;
    }

    public void applyContent(CardContent content) {
        this.title = content.title();
        this.onsetStatus = content.onsetStatus();
        this.onsetText = content.onsetText();
        this.patternStatus = content.patternStatus();
        this.patternText = content.patternText();
        this.siteStatus = content.siteStatus();
        this.siteText = content.siteText();
        this.siteCodes = new ArrayList<>(content.siteCodes());
        this.medicationsStatus = content.medicationsStatus();
        this.medications = new ArrayList<>(content.medications());
        this.allergiesStatus = content.allergiesStatus();
        this.allergiesText = content.allergiesText();
        this.questions = new ArrayList<>(content.questions());
        this.suggestedDepartment = content.suggestedDepartment();
        this.evidence = new LinkedHashMap<>(content.evidence());
    }

    public void applyTrace(String pipelineVersion, String aiRequestId) {
        this.pipelineVersion = pipelineVersion;
        this.aiRequestId = aiRequestId;
    }

    // --- 상태 전이 ---

    public boolean isEditable() {
        return status == CardStatus.DRAFT;
    }

    public void confirm() {
        this.status = CardStatus.CONFIRMED;
        this.confirmedAt = Instant.now();
    }

    /**
     * 확정된 카드를 고치려 할 때, 이 카드를 이어받은 새 초안을 만든다.
     *
     * <p>원본은 그대로 둔다. 의사가 이미 본 카드이기 때문이다.
     */
    public BriefingCard newVersion() {
        BriefingCard next = new BriefingCard(user, session);
        next.version = this.version + 1;
        next.parentCard = this;
        next.applyPatientSnapshot(patientName, patientAge, patientSex);
        next.applyTrace(pipelineVersion, aiRequestId);
        next.copyContentFrom(this);
        return next;
    }

    private void copyContentFrom(BriefingCard source) {
        this.title = source.title;
        this.onsetStatus = source.onsetStatus;
        this.onsetText = source.onsetText;
        this.patternStatus = source.patternStatus;
        this.patternText = source.patternText;
        this.siteStatus = source.siteStatus;
        this.siteText = source.siteText;
        this.siteCodes = new ArrayList<>(source.siteCodes);
        this.medicationsStatus = source.medicationsStatus;
        this.medications = new ArrayList<>(source.medications);
        this.allergiesStatus = source.allergiesStatus;
        this.allergiesText = source.allergiesText;
        this.questions = new ArrayList<>(source.questions);
        this.suggestedDepartment = source.suggestedDepartment;
        this.evidence = new LinkedHashMap<>(source.evidence);
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
