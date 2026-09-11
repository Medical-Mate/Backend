package com.jinryomate.backend.card.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 브리핑 카드. 화면 S3({@code 1e}).
 *
 * <p>환자 인적사항은 <b>만든 시점 값을 그대로 박아둔다</b>. 프로필을 참조하면 나중에
 * 이름을 고쳤을 때 이미 확정된 카드까지 바뀐다. 의사가 본 카드는 그대로 남아야 한다.
 *
 * <p>{@link CardStatus#CONFIRMED} 이후의 변경은 이 행을 고치지 않고
 * {@link #newVersion()} 으로 새 행을 만든다. {@code version} 과 {@code parentCard} 로 체인이 된다.
 *
 * <p><b>본문은 AI 계약의 축 구조를 그대로 담는다.</b> 컬럼 여덟 벌로 펼치지 않는 이유는
 * 축이 늘거나 줄 때마다 마이그레이션이 필요해지고, "축마다 같은 세 값을 갖는다"는 구조가
 * 스키마에서 사라지기 때문이다.
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

    /**
     * AI 가 부위 + 기간을 결정론으로 조합해 만든다({@code 왼쪽 무릎 · 3일}).
     *
     * <p><b>우리가 검증하지 않는다.</b> LLM 을 거치지 않는 값의 조합이라 병명이 들어갈
     * 경로가 없다. 아직 안 내려와서 null 일 수 있다.
     */
    @Column(length = 40)
    private String title;

    /** 환자가 말한 그대로. <b>줄이지 않는다</b> — 줄이는 순간 환자 말이 아니다. */
    @Column(columnDefinition = "text")
    private String chiefComplaint;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "briefing_card_axes", joinColumns = @JoinColumn(name = "card_id"))
    private List<CardAxis> axes = new ArrayList<>();

    /** 의료인 자문 전 자리. 지금은 빈 배열로 온다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> redFlags = new ArrayList<>();

    /** 축 밖으로 새는 환자 말. 버리면 "타이레놀 먹었어요" 같은 게 사라진다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> patientNotes = new ArrayList<>();

    /** 환자가 의사에게 묻고 싶어 하는 것. 최대 3개. 확정 시점의 스냅샷이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> questions = new ArrayList<>();

    /**
     * 진료과 안내. <b>배열이다</b> — 하나로 좁히는 순간 그게 추천이 된다.
     *
     * <p>우리가 판정하지 않는다. 환자가 짚은 부위 노드의 속성을 그대로 담는다.
     * 진료과가 없는 부위 14곳에서는 빈 목록이다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> departmentGuidance = new ArrayList<>();

    /** {@code "팀 결정 2026-09-04, 의료인 자문 확인 전"}. 화면에 함께 보여야 한다. */
    @Column(length = 120)
    private String departmentGuidanceSource;

    /** 8축 중 몇 개가 찼는지. 앱이 "조금 더 여쭤볼까요"를 띄우는 근거다. */
    private Double completeness;

    private Boolean minimallyComplete;

    // --- 추적성 ---

    /**
     * 어떤 프롬프트가 만든 카드인지. 프롬프트는 AI 레포에서 관리하므로,
     * 백엔드가 추적성을 확보하는 유일한 수단이다.
     */
    @Column(length = 64)
    private String promptVersion;

    /** 프롬프트가 같아도 모델이 바뀌면 결과가 달라진다. 하나로는 못 짚는다. */
    @Column(length = 64)
    private String modelId;

    @Column(length = 32)
    private String ontologySnapshot;

    /** 장애 시 AI 쪽 로그와 잇는 열쇠. */
    @Column(length = 64)
    private String aiRequestId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant confirmedAt;

    /**
     * 환자가 이 카드를 의사에게 보여준 시각.
     *
     * <p>S5 진료 후 기록을 재촉할 근거가 이 값이다. 전달한 적 없는 카드에
     * "진료 어떠셨어요"를 물으면 안 된다.
     */
    private Instant handedOffAt;

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
        this.chiefComplaint = content.chiefComplaint();
        this.axes = content.axes().values().stream().map(CardAxis::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        this.redFlags = new ArrayList<>(content.redFlags());
        this.patientNotes = new ArrayList<>(content.patientNotes());
        this.questions = new ArrayList<>(content.questions());
        this.departmentGuidance = new ArrayList<>(content.departmentGuidance());
        this.departmentGuidanceSource = content.departmentGuidanceSource();
        this.completeness = content.completeness();
        this.minimallyComplete = content.minimallyComplete();
    }

    public void applyTrace(String promptVersion, String modelId, String ontologySnapshot, String aiRequestId) {
        this.promptVersion = promptVersion;
        this.modelId = modelId;
        this.ontologySnapshot = ontologySnapshot;
        this.aiRequestId = aiRequestId;
    }

    /**
     * 목록·일정·홈에서 이 카드를 가리킬 이름.
     *
     * <p>{@code title} 은 AI 가 부위 + 기간을 조합해 만드는데 <b>아직 안 내려온다</b>.
     * 그동안 목록이 통째로 비면 환자가 자기 카드를 못 찾는다. 그래서 없으면 환자가 말한
     * 그대로({@code chiefComplaint})를 쓴다.
     *
     * <p><b>줄이지 않는다.</b> 20자에 맞추려고 자르는 순간 그건 이미 환자 말이 아니다 —
     * 길면 화면에서 줄이는 것은 앱 몫이다. 서버가 문구를 만들지 않는다는 원칙은 그대로다.
     * 여기서 하는 일은 있는 값 둘 중 하나를 고르는 것뿐이다.
     */
    public String displayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (chiefComplaint != null && !chiefComplaint.isBlank()) {
            return chiefComplaint;
        }
        // 문답을 시작만 하고 아무 말도 안 한 카드다. 부위라도 없으면 목록에서
        // 자기 카드를 못 찾는다.
        return session == null ? null : session.getSiteText();
    }

    /** 축 이름으로 찾아 쓰기 좋게. 순서는 AI 가 준 그대로 유지한다. */
    public Map<String, CardAxis> axesByName() {
        return axes.stream().collect(Collectors.toMap(
                CardAxis::getAxis, Function.identity(), (a, b) -> a, LinkedHashMap::new));
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
        next.applyTrace(promptVersion, modelId, ontologySnapshot, aiRequestId);
        next.title = title;
        next.chiefComplaint = chiefComplaint;
        next.axes = axes.stream().map(CardAxis::copy)
                .collect(Collectors.toCollection(ArrayList::new));
        next.redFlags = new ArrayList<>(redFlags);
        next.patientNotes = new ArrayList<>(patientNotes);
        next.questions = new ArrayList<>(questions);
        next.departmentGuidance = new ArrayList<>(departmentGuidance);
        next.departmentGuidanceSource = departmentGuidanceSource;
        next.completeness = completeness;
        next.minimallyComplete = minimallyComplete;
        return next;
    }

    // --- 전달 ---

    /** 확정된 카드만 의사에게 보여줄 수 있다. 초안을 의사가 보면 안 된다. */
    public boolean isHandoffReady() {
        return status == CardStatus.CONFIRMED;
    }

    /** 처음 보여준 시각만 남긴다. 다시 열었다고 진료 시각이 뒤로 밀리지는 않는다. */
    public void markHandedOff() {
        if (handedOffAt == null) {
            this.handedOffAt = Instant.now();
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
