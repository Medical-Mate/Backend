package com.jinryomate.backend.visit.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

    /** 어느 진료의 기록인지. 카드 하나에 기록 하나다. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, unique = true)
    private BriefingCard card;

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

    @OneToMany(mappedBy = "visitRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<ComprehensionCheck> checks = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private VisitRecord(User user, BriefingCard card, LocalDate visitedOn) {
        this.user = user;
        this.card = card;
        this.visitedOn = visitedOn;
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

    public void addCheck(ComprehensionCheck check) {
        checks.add(check);
    }

    public int nextSeq() {
        return checks.size() + 1;
    }

    /** 아직 답하지 않은 문항 수. 앱이 "3문항 중 2"를 표시하는 데 쓴다. */
    public long answeredCount() {
        return checks.stream().filter(ComprehensionCheck::isAnswered).count();
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
