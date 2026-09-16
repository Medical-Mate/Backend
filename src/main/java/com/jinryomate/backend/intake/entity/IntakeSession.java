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

    /**
     * 대화 턴 상한. AI 계약의 안전장치 값과 같다.
     *
     * <p><b>와이어프레임의 "2 / 4"가 아니다.</b> 그 넷은 화면 단계
     * (부위 짚기 → 문답 → 통증 강도 → 의사에게 물어볼 것)이고, 이 숫자는 <b>2단계 안에서</b>
     * 주고받는 대화 턴이다. 층이 다르다.
     *
     * <p>화면 단계 진행도는 <b>앱이 안다</b> — 화면 전환을 앱이 하므로 서버가 내려줄 것이
     * 없다(AI#7 확인). 여기 값은 "대화가 얼마나 남았나"일 뿐이다.
     *
     * <p>정상 문답은 6턴(시작·느낌·경과·악화완화·퍼짐·동반)이다. 20은 안전장치다.
     */
    public static final int MAX_TURNS = 20;

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
     * 부위 마스터의 노드 id. {@code ANC:001}(앵커) · {@code SUR:032}(구역).
     *
     * <p><b>하나만 받는다.</b> AI 계약이 세션 시작에 부위 하나를 받는다. 부위 여러 개는
     * AI 저장소 #8 에서 팀 결정 대기 중이고, 정해지면 양쪽 계약을 함께 고친다.
     *
     * <p>부위를 건너뛸 수 있어 비어 있을 수 있다.
     */
    @Column(length = 40)
    private String siteNodeId;

    /** 좌우. {@code laterality} 가 {@code left_right} 인 부위에만 붙는다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Side side;

    /**
     * 추출을 어디서 돌리는지. 앱이 정해 보내고 우리는 AI 에 그대로 넘긴다.
     *
     * <p>세션 시작에 한 번 정해지고 턴마다 바뀌지 않아서 여기 둔다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiProfile aiProfile = AiProfile.SERVER;

    /** 사람이 읽는 표현. 예: {@code 손가락 관절(오른손)}. 문답 첫 문장에 그대로 들어간다. */
    @Column(length = 100)
    private String siteText;

    @Column(nullable = false)
    private int progressCurrent = 0;

    @Column(nullable = false)
    private int progressTotal = MAX_TURNS;

    /**
     * AI 가 준 {@code state} 원문.
     *
     * <p><b>열어보지 않는다.</b> AI 계약이 불투명하게 다루라고 명시했고, 내부 구조에
     * 의존하는 순간 AI 쪽 변경이 우리를 깨뜨린다. 턴마다 통째로 덮어쓸 뿐이다.
     */
    @Column(columnDefinition = "TEXT")
    private String aiState;

    /**
     * AI 가 마지막 턴에 준 카드 원문.
     *
     * <p><b>카드는 매 턴 오지만 턴마다 카드 행을 만들지는 않는다.</b> 그러면 버전 체인이
     * 의미를 잃는다 — 환자가 고치지도 않았는데 버전이 스무 개가 된다. 여기 덮어써 두었다가
     * 앱이 카드를 요청할 때 이 값으로 만든다.
     *
     * <p>{@code aiState} 와 달리 <b>이건 열어본다.</b> 화면에 보여줄 값이 들어 있기 때문이다.
     * 다만 파싱은 카드를 만들 때 한 번만 한다.
     */
    @Column(columnDefinition = "TEXT")
    private String aiCard;

    /** 왜 끝났는지. {@code stop} · {@code complete} · {@code max_turns} · {@code budget}. */
    @Column(length = 16)
    private String endReason;

    /**
     * 통증 강도. 화면 3단계({@code 1d})에서 슬라이더로 받는다.
     *
     * <p><b>1~5 서열척도다. NRS 0~10 이 아니다.</b> 와이어프레임의 슬라이더가 다섯 칸이다.
     */
    private Integer severityLevel;

    /**
     * "꽤 아파요" 같은 표시 문구. <b>앱이 보낸다.</b>
     *
     * <p>서버가 들고 있으면 문구를 바꿀 때마다 배포해야 하고, 이건 디자인 카피라
     * 우리 것이 아니다. AI 에 {@code selections} 로 넘길 때 {@code "3 (꽤 아파요)"} 를
     * 조립하는 데 쓴다.
     */
    @Column(length = 40)
    private String severityLabel;

    /**
     * 의사에게 물어볼 것. 화면 4단계({@code 1i}).
     *
     * <p><b>최종 목록은 우리 것이다.</b> AI 는 후보({@code question_candidates})만 내고,
     * 환자가 고른 것과 직접 쓴 것을 합치는 것은 앱·백엔드 몫이다(AI#7 확인).
     *
     * <p>순서가 화면에 번호(①②③)로 보이므로 {@code @OrderColumn} 으로 고정한다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "intake_session_questions",
                     joinColumns = @JoinColumn(name = "session_id"))
    @OrderColumn(name = "seq")
    @Column(name = "text", nullable = false, length = 200)
    private List<String> questions = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<IntakeMessage> messages = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    private IntakeSession(User user, String siteNodeId, Side side, String siteText, AiProfile aiProfile) {
        this.user = user;
        this.siteNodeId = siteNodeId;
        this.side = side;
        this.siteText = siteText;
        this.aiProfile = aiProfile == null ? AiProfile.SERVER : aiProfile;
    }

    /** S1.5에서 부위를 짚고 시작한다. 부위를 건너뛰면 비어 있을 수 있다. */
    public static IntakeSession start(User user, String siteNodeId, Side side, String siteText,
                                      AiProfile aiProfile) {
        return new IntakeSession(user, siteNodeId, side, siteText, aiProfile);
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

    /** AI 가 준 state 를 통째로 덮어쓴다. 턴마다 호출된다. */
    public void rememberState(String aiState) {
        this.aiState = aiState;
    }

    /**
     * AI 가 준 카드를 통째로 덮어쓴다. 턴마다 호출된다.
     *
     * <p>{@code null} 이면 덮어쓰지 않는다. 카드가 빠진 응답 하나 때문에 쌓아둔 것을
     * 잃으면 안 된다.
     */
    public void rememberCard(String aiCard) {
        if (aiCard != null) {
            this.aiCard = aiCard;
        }
    }

    public void complete() {
        complete(null);
    }

    /**
     * 문답을 끝낸다.
     *
     * <p>이미 끝난 세션에 다시 불러도 {@code completedAt} 을 덮어쓰지 않는다.
     * AI 계약상 종료 후에도 턴이 올 수 있는데, 그때마다 완료 시각이 밀리면
     * "언제 끝난 문답인지"를 잃는다.
     */
    public void complete(String endReason) {
        if (this.status == Status.COMPLETED) {
            return;
        }
        this.status = Status.COMPLETED;
        this.endReason = endReason;
        this.completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    /**
     * 통증 강도를 기록한다. 다시 고르면 덮어쓴다.
     *
     * <p>3단계는 문답이 <b>끝난 뒤</b> 화면이라 {@code COMPLETED} 세션에도 쓸 수 있어야 한다.
     */
    public void recordSeverity(int level, String label) {
        this.severityLevel = level;
        this.severityLabel = label;
    }

    /**
     * 의사에게 물어볼 것을 통째로 교체한다.
     *
     * <p>추가·편집·삭제·순서변경이 전부 "목록을 다시 보내기" 하나로 처리된다.
     * 몇 개 안 되는 목록에 엔드포인트를 여러 개 둘 이유가 없다.
     */
    public void replaceQuestions(List<String> questions) {
        this.questions.clear();
        if (questions != null) {
            this.questions.addAll(questions);
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
