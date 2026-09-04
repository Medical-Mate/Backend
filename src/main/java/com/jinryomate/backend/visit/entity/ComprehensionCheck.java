package com.jinryomate.backend.visit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 되묻기 한 문항. 화면 S5의 "이해 확인 · 3문항 중 2".
 *
 * <p>환자가 제대로 이해했는지 확인하는 게 목적이라 <b>틀려도 넘어간다</b>. 시험이 아니다.
 * 오답이면 그 자리에서 정정 문구를 보여주고 다음으로 간다.
 */
@Entity
@Table(name = "comprehension_checks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComprehensionCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_record_id", nullable = false)
    private VisitRecord visitRecord;

    @Column(nullable = false)
    private int seq;

    /** 예: {@code 약은 하루 몇 번 드시면 되죠?} */
    @Column(nullable = false, length = 200)
    private String question;

    /** 예: {@code 두 번이에요. 아침저녁 식후로.} 채점과 정정 문구의 근거다. */
    @Column(nullable = false, length = 200)
    private String expectedAnswer;

    @Column(length = 500)
    private String userAnswer;

    /** 아직 답하지 않았으면 null. 답했으면 정답 여부가 들어간다. */
    private Boolean correct;

    private Instant answeredAt;

    private ComprehensionCheck(VisitRecord visitRecord, int seq, String question, String expectedAnswer) {
        this.visitRecord = visitRecord;
        this.seq = seq;
        this.question = question;
        this.expectedAnswer = expectedAnswer;
    }

    public static ComprehensionCheck of(VisitRecord visitRecord, String question, String expectedAnswer) {
        return new ComprehensionCheck(visitRecord, visitRecord.nextSeq(), question, expectedAnswer);
    }

    public void answer(String userAnswer, boolean correct) {
        this.userAnswer = userAnswer;
        this.correct = correct;
        this.answeredAt = Instant.now();
    }

    public boolean isAnswered() {
        return correct != null;
    }
}
