package com.jinryomate.backend.visit.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.card.entity.CardAxis;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
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
 * 진료 후 기록. 화면 S5.
 *
 * <p>병원을 나온 직후 골든타임에 받아 적는다. 환자가 적은 메모를 AI 가 소견·검사·약·재방문
 * 으로 나누고, 그 결과가 시안 1q-1 의 카드 한 장에 찍힌다.
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

    /**
     * AI 가 나눈 항목. 화면 {@code 1q-1}.
     *
     * <p><b>칸을 고정하지 않는다.</b> 시안은 브리핑 카드와 같은 모양의 카드 한 장이고,
     * "소견"을 못 찾으면 그 줄이 없고 다른 항목을 찾으면 그 줄이 생긴다. 세 칸으로 박아두면
     * 그중 셋만 살아남는다.
     *
     * <p>카드의 {@link CardAxis} 를 그대로 쓴다. 모양이 같아야 앱이 그리는 코드를 나눠 쓰고,
     * 두 벌로 두면 한쪽만 고쳐져서 갈라진다.
     *
     * <p>AI 가 주는 네 축은 {@code findings}(소견) · {@code tests}(검사) ·
     * {@code medication_instructions}(약) · {@code follow_up}(재방문)이다. 다만 축 이름을
     * enum 으로 박지 않으므로 늘어도 저장된다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "visit_record_axes", joinColumns = @JoinColumn(name = "visit_id"))
    private List<CardAxis> axes = new ArrayList<>();

    /**
     * 재방문 시점.
     *
     * <p><b>날짜 하나가 아니다.</b> 환자가 들은 말은 "2주 뒤" 이고, 그걸 날짜로 바꾼 것이
     * {@code followUpDate}, 그 날짜가 정확한 지정이 아니라는 표시가
     * {@code followUpApproximate} 다. 시안 1q-1 은 셋을 합쳐 "2주 뒤 (9월 27일 전후)" 로 찍는다.
     *
     * <p><b>여기서 일정을 만들지 않는다.</b> 앱이 이 값을 읽어
     * {@code POST /api/me/appointments} 를 부른다 — 환자가 확인하고 등록하는 흐름이고,
     * AI 가 "2주 뒤"를 잘못 계산해도 조용히 일정이 생기지 않는다.
     */
    private LocalDate followUpDate;

    /** 환자가 말한 그대로. {@code 2주 뒤} */
    @Column(length = 60)
    private String followUpText;

    /** {@code true} 면 화면에 "전후" 를 붙인다. 재방문이 없으면 {@code null}. */
    private Boolean followUpApproximate;

    /** 어느 축에도 안 들어간 문장. <b>버리지 않는다.</b> */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> patientNotes = new ArrayList<>();

    /** 무엇이 이 기록을 나눴는지. 환자가 직접 적었으면 비어 있다. */
    @Column(length = 40)
    private String promptVersion;

    @Column(length = 80)
    private String modelId;

    /**
     * 환자가 순서 없이 말한 원문.
     *
     * <p>"어떤 얘기 들으셨어요? 순서 없이 생각나는 대로 괜찮아요"에 대한 답이다.
     * 축은 여기서 나눠 낸 것이고, 원문도 남겨 나중에 다시 볼 수 있게 한다.
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

    /**
     * 항목을 통째로 갈아끼운다.
     *
     * <p>부분 수정이 아니라 통째로인 이유 — 화면 {@code 1q-1-E} 가 "전체 수정"이다. 환자가
     * 줄을 지우면 그 축이 사라져야 하는데, 부분 병합이면 지운 줄이 남는다.
     *
     * @param axes {@code null} 이면 그대로 둔다. 빈 목록이면 <b>전부 지운다</b>
     */
    public void applyAxes(List<CardAxis> axes) {
        if (axes == null) {
            return;
        }
        this.axes = axes.stream().map(CardAxis::copy)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public void applyContent(String clinicName, FollowUp followUp,
                             List<String> patientNotes, String rawNote) {
        this.clinicName = clinicName;
        this.patientNotes = patientNotes == null ? new ArrayList<>() : new ArrayList<>(patientNotes);
        this.rawNote = rawNote;

        FollowUp value = followUp == null ? FollowUp.NONE : followUp;
        this.followUpDate = value.date();
        this.followUpText = value.text();
        // 재방문이 없으면 null 이다. false 로 박으면 "정확한 날짜를 지정받았다"가 되어 뜻이 다르다.
        this.followUpApproximate = value.isEmpty() ? null : value.approximate();
    }

    /** 저장된 재방문 시점. 셋이 늘 함께 다닌다. */
    public FollowUp followUp() {
        return new FollowUp(followUpDate, followUpText,
                followUpApproximate != null && followUpApproximate);
    }

    /** 무엇이 나눴는지. 환자가 직접 적은 기록이면 부르지 않는다. */
    public void applyTrace(String promptVersion, String modelId) {
        this.promptVersion = promptVersion;
        this.modelId = modelId;
    }

    public void changeVisitedOn(LocalDate visitedOn) {
        if (visitedOn != null) {
            this.visitedOn = visitedOn;
        }
    }

    /** 축 이름으로 찾아 쓰기 좋게. 순서는 AI 가 준 그대로 유지한다. */
    public Map<String, CardAxis> axesByName() {
        return axes.stream().collect(Collectors.toMap(
                CardAxis::getAxis, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** V17 이전 행은 이 컬럼이 비어 있다. */
    public List<String> getPatientNotes() {
        return patientNotes == null ? List.of() : patientNotes;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
