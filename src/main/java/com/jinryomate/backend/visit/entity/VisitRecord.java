package com.jinryomate.backend.visit.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진료 후 기록. 화면 S5.
 *
 * <p>병원을 나온 직후 골든타임에 받아 적는다. 여기 담긴 한 일·결과·받은 약이
 * 와이어프레임의 요약 카드에 그대로 찍힌다.
 *
 * <p><b>녹음은 저장하지 않는다.</b> 기억 재구성 방식이라 오디오 컬럼 자체를 만들지 않는다.
 * 컬럼이 없으면 실수로 저장할 수도 없다.
 */
@Entity
@Table(name = "visit_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 어느 진료의 기록인지. 카드 하나에 기록 하나다.
     *
     * <p><b>카드가 지워지면 끊어지고 기록은 남는다.</b> 카드는 진료 전에 만든 준비물이고
     * 기록은 진료에서 실제로 들은 것이라, 준비물을 지웠다고 의사에게 들은 말이 사라지면 안 된다.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", unique = true)
    private BriefingCard card;

    /**
     * 카드를 만들 때의 제목. 카드가 지워진 뒤 목록에 줄 제목을 그리는 데 쓴다.
     *
     * <p><b>카드가 살아 있는 동안은 이 값을 쓰지 않는다.</b> 환자가 카드를 고치면 제목이
     * 따라가야 하므로 카드의 현재 제목을 읽고, 카드가 사라진 뒤에만 여기로 내려간다.
     */
    @Column(length = 120)
    private String cardTitle;

    // --- 요약 카드에 찍히는 값 ---

    @Column(length = 60)
    private String clinicName;

    @Column(nullable = false)
    private LocalDate visitedOn;

    /** 예: {@code 혈액검사(류마티스 인자 포함)} */
    @Column(length = 200)
    private String whatWasDone;

    /** 예: {@code 3일 뒤 확인} */
    @Column(length = 200)
    private String result;

    /** 예: {@code 나프록센 500mg·하루 2번 식후} */
    @Column(length = 200)
    private String prescription;

    /**
     * 환자가 순서 없이 말한 원문.
     *
     * <p>"어떤 얘기 들으셨어요? 순서 없이 생각나는 대로 괜찮아요"에 대한 답이다.
     * 위 세 필드는 여기서 정리해 낸 것이고, 원문도 남겨 나중에 다시 볼 수 있게 한다.
     */
    @Column(length = 2000)
    private String rawNote;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private VisitRecord(User user, BriefingCard card, LocalDate visitedOn) {
        this.user = user;
        this.card = card;
        this.visitedOn = visitedOn;
        this.cardTitle = card == null ? null : card.displayTitle();
    }

    /**
     * 카드 연결을 끊는다. 카드를 지울 때 쓴다.
     *
     * <p>제목 스냅샷은 그대로 둔다 — 그게 이 값이 있는 이유다.
     */
    public void detachCard() {
        this.card = null;
    }

    /**
     * 목록에 그릴 줄 제목.
     *
     * <p>카드가 있으면 <b>현재 제목</b>을, 지워졌으면 만들 때 박아둔 제목을 쓴다.
     */
    public String displayCardTitle() {
        return card != null ? card.displayTitle() : cardTitle;
    }

    public static VisitRecord of(User user, BriefingCard card, LocalDate visitedOn) {
        return new VisitRecord(user, card, visitedOn == null ? LocalDate.now() : visitedOn);
    }

    public void applyContent(String clinicName, String whatWasDone, String result,
                             String prescription, String rawNote) {
        this.clinicName = clinicName;
        this.whatWasDone = whatWasDone;
        this.result = result;
        this.prescription = prescription;
        this.rawNote = rawNote;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
