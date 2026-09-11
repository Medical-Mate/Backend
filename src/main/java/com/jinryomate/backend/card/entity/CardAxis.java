package com.jinryomate.backend.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 카드 축 하나. SOCRATES 8축 중 하나에 해당한다.
 *
 * <p>축 이름을 enum 이 아니라 문자열로 둔다. <b>AI 가 축을 하나 늘리면 우리 기동이
 * 막히기 때문이다.</b> 모르는 축이 와도 저장하고, 무엇을 보여줄지는 앱이 정한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardAxis {

    /** {@code site} · {@code onset} · {@code character} · {@code radiation} 등. */
    @Column(name = "axis", nullable = false, length = 32)
    private String axis;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AxisStatus status = AxisStatus.NOT_ASKED;

    @Column(name = "value", length = 200)
    private String value;

    /** 환자 발화 원문. 한 축이 여러 발화에서 채워질 수 있어 배열이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private List<String> evidence = new ArrayList<>();

    /** AI 가 아직 안 내려준다. 환자가 고친 축에는 우리가 {@link AxisSource#PATIENT_EDIT} 를 넣는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16)
    private AxisSource source;

    private CardAxis(String axis, AxisStatus status, String value, List<String> evidence, AxisSource source) {
        this.axis = axis;
        this.status = status == null ? AxisStatus.NOT_ASKED : status;
        this.value = value;
        this.evidence = evidence == null ? new ArrayList<>() : new ArrayList<>(evidence);
        this.source = source;
    }

    public static CardAxis of(String axis, AxisStatus status, String value,
                              List<String> evidence, AxisSource source) {
        return new CardAxis(axis, status, value, evidence, source);
    }

    /** 아직 묻지 않은 빈 축. */
    public static CardAxis notAsked(String axis) {
        return new CardAxis(axis, AxisStatus.NOT_ASKED, null, List.of(), null);
    }

    /**
     * 환자가 고친 값으로 바꾼 새 축을 만든다.
     *
     * <p>{@code evidence} 를 비우지 않고 환자가 쓴 값을 근거로 남긴다. 비워두면 의사 화면에서
     * "근거 없는 값"이 되는데, 실제로는 환자 본인이 적은 것이라 근거가 없는 게 아니다.
     */
    public CardAxis editedByPatient(String newValue) {
        return new CardAxis(
                axis,
                newValue == null || newValue.isBlank() ? AxisStatus.UNKNOWN : AxisStatus.FILLED,
                newValue,
                newValue == null || newValue.isBlank() ? List.of() : List.of("[환자 수정] " + newValue),
                AxisSource.PATIENT_EDIT);
    }

    public CardAxis copy() {
        return new CardAxis(axis, status, value, evidence, source);
    }
}
