package com.jinryomate.backend.appointment.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
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

    /** 카드 없이도 일정을 만들 수 있다. 캘린더의 {@code +} 버튼이 그 경로다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    private BriefingCard card;

    @Column(nullable = false, length = 60)
    private String clinicName;

    @Column(length = 40)
    private String department;

    /** "재진" 같은 짧은 메모. */
    @Column(length = 60)
    private String purpose;

    /** 날짜만이 아니라 시각까지. 화면이 "오전 10:30"을 보여준다. */
    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.SCHEDULED;

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

    private Appointment(User user, String clinicName, Instant scheduledAt) {
        this.user = user;
        this.clinicName = clinicName;
        this.scheduledAt = scheduledAt;
    }

    public static Appointment of(User user, String clinicName, Instant scheduledAt) {
        return new Appointment(user, clinicName, scheduledAt);
    }

    /**
     * 카드 연결을 끊는다. 카드를 지울 때 쓴다.
     *
     * <p><b>일정은 남는다.</b> 카드 없이 만드는 경로가 이미 있어서, 카드를 지웠다고 병원
     * 예약까지 사라지면 환자가 진료를 놓친다.
     */
    public void detachCard() {
        this.card = null;
    }

    public void applyDetails(String department, String purpose, BriefingCard card) {
        this.department = department;
        this.purpose = purpose;
        this.card = card;
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
     * <p>카드 연결 해제는 {@code clearCard} 로 따로 받는다. null 을 "안 바꿈"과
     * "지움" 두 뜻으로 쓰면 연결을 끊을 방법이 없다.
     */
    public void update(String clinicName, String department, String purpose,
                       Instant scheduledAt, Status status,
                       BriefingCard card, boolean clearCard) {
        if (clinicName != null) {
            this.clinicName = clinicName;
        }
        if (department != null) {
            this.department = department;
        }
        if (purpose != null) {
            this.purpose = purpose;
        }
        if (scheduledAt != null) {
            this.scheduledAt = scheduledAt;
        }
        if (status != null) {
            this.status = status;
        }
        if (clearCard) {
            this.card = null;
        } else if (card != null) {
            this.card = card;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
