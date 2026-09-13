package com.jinryomate.backend.appointment.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 진료 예정 일정. 화면 1r · 1n.
 *
 * <p>병원명과 진료과를 나눠 둔다. 병원 검색(카카오 로컬)이 붙으면 {@code place_name} 과
 * {@code category_name} 이 따로 오므로 그대로 채울 수 있다. 화면의 "서울OO병원 내과 재진"은
 * 앱이 세 필드를 조합해 만든다.
 */
@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appointment {

    public enum Status {
        /** 아직 가기 전. */
        SCHEDULED,
        /** 다녀왔다. 진료 후 기록을 남기면 여기로 옮긴다. */
        DONE,
        /** 취소했다. 지우지 않고 남기면 "왜 안 갔는지"가 캘린더에 보인다. */
        CANCELED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 이 진료에 가져갈 브리핑 카드. 화면 {@code 1r-4-B} 의 "가져갈 브리핑 카드".
     *
     * <p><b>여러 장이다.</b> 그 화면이 체크박스이고 개수를 찍는다. 카드 쪽에
     * {@code appointment_id} 를 두면 조인 테이블이 없어도 되지만, 그러면 카드 하나가
     * 일정 하나에만 붙어서 <b>같은 카드를 두 진료에 가져가는 것</b>을 막는다.
     *
     * <p>카드 없이도 일정을 만들 수 있다. 캘린더의 {@code +} 버튼이 그 경로다.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "appointment_cards",
            joinColumns = @JoinColumn(name = "appointment_id"),
            inverseJoinColumns = @JoinColumn(name = "card_id"))
    private List<BriefingCard> cards = new ArrayList<>();

    @Column(nullable = false, length = 60)
    private String clinicName;

    @Column(length = 40)
    private String department;

    /** "재진" 같은 짧은 메모. */
    @Column(length = 60)
    private String purpose;

    /**
     * 언제. <b>날짜만 필수다.</b>
     *
     * <p>시안 {@code 1r-2-A} 에 "재방문 예정 / 시간 정하고 확정하기" 가 있다 — 날짜는
     * 아는데 시각은 아직 모르는 상태가 화면에 있다.
     *
     * <p>{@code Instant} 하나로 두지 않는 이유가 하나 더 있다. 그러면 "9월 26일"이
     * 시간대에 따라 25일이 되기도 해서 월·일자 경계를 KST 로 고정해 잘라야 했다.
     * {@code DATE} 로 두면 그 계산이 통째로 사라진다.
     */
    @Column(nullable = false)
    private LocalDate scheduledOn;

    /** 몇 시. 안 정했으면 {@code null} 이고, 화면은 "시간 정하고 확정하기" 를 띄운다. */
    private LocalTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.SCHEDULED;

    /**
     * 이 일정이 어디서 왔는지.
     *
     * <p>시안 {@code 1r-2-A} 가 "진료 후 기록에서 자동으로 만들었어요" 를 찍는다.
     * 환자가 손으로 만든 일정과 구별이 안 되면 그 문구를 못 그린다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Origin origin = Origin.MANUAL;

    /**
     * 진료 전 할 일. 화면 1r-4 에서 적고 1r-2 에서 체크한다.
     *
     * <p><b>목록째 갈아끼운다.</b> 줄 하나만 따로 조회할 일이 없고, 체크를 켤 때도 앱이
     * 화면에 있는 목록을 그대로 보낸다. 별도 테이블을 파면 조인만 늘고 얻는 게 없다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<AppointmentTodo> todos = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Appointment(User user, String clinicName, LocalDate scheduledOn) {
        this.user = user;
        this.clinicName = clinicName;
        this.scheduledOn = scheduledOn;
    }

    public static Appointment of(User user, String clinicName, LocalDate scheduledOn) {
        return new Appointment(user, clinicName, scheduledOn);
    }

    /**
     * 이 카드들의 연결을 끊는다. 카드를 지울 때 쓴다.
     *
     * <p><b>일정은 남는다.</b> 카드 없이 만드는 경로가 이미 있어서, 카드를 지웠다고 병원
     * 예약까지 사라지면 환자가 진료를 놓친다.
     *
     * <p>한 장이 아니라 목록을 받는다 — 카드를 지울 때는 같은 문답의 <b>버전 전체</b>가
     * 함께 지워지고, 일정에 어느 버전이 붙어 있는지는 여기서 알 바가 아니다.
     */
    public void detachCards(Collection<Long> cardIds) {
        cards.removeIf(c -> cardIds.contains(c.getId()));
    }

    public void applyDetails(String department, String purpose, Origin origin) {
        this.department = department;
        this.purpose = purpose;
        if (origin != null) {
            this.origin = origin;
        }
    }

    /**
     * 가져갈 카드를 통째로 갈아끼운다.
     *
     * @param cards {@code null} 이면 그대로 둔다. 빈 목록이면 <b>전부 뗀다</b> —
     *              체크를 다 풀었을 때 그게 남으면 안 된다
     */
    public void applyCards(List<BriefingCard> cards) {
        if (cards == null) {
            return;
        }
        this.cards = new ArrayList<>(cards);
    }

    /** 시각을 정한다. 화면 {@code 1r-2-A} 의 "시간 정하고 확정하기". */
    public void applySchedule(LocalDate scheduledOn, LocalTime scheduledTime, boolean clearTime) {
        if (scheduledOn != null) {
            this.scheduledOn = scheduledOn;
        }
        if (clearTime) {
            this.scheduledTime = null;
        } else if (scheduledTime != null) {
            this.scheduledTime = scheduledTime;
        }
    }

    /**
     * 할 일을 통째로 갈아끼운다.
     *
     * @param todos {@code null} 이면 그대로 둔다. 빈 목록이면 <b>전부 지운다</b> —
     *              환자가 마지막 줄을 지웠을 때 그게 남으면 안 된다
     */
    public void applyTodos(List<AppointmentTodo> todos) {
        if (todos == null) {
            return;
        }
        this.todos = new ArrayList<>(todos);
    }

    /** V18 이전 행은 이 컬럼이 비어 있다. */
    public List<AppointmentTodo> getTodos() {
        return todos == null ? List.of() : todos;
    }

    /**
     * 수정. 넘어온 값만 바꾼다.
     *
     * <p>카드는 {@link #applyCards(List)}, 일시는 {@link #applySchedule} 로 따로 받는다.
     * 둘 다 "안 바꿈"과 "비움"을 갈라야 해서 여기 섞으면 인자가 뜻을 잃는다.
     */
    public void update(String clinicName, String department, String purpose, Status status) {
        if (clinicName != null) {
            this.clinicName = clinicName;
        }
        if (department != null) {
            this.department = department;
        }
        if (purpose != null) {
            this.purpose = purpose;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
