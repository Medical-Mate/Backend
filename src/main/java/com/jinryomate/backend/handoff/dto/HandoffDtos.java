package com.jinryomate.backend.handoff.dto;

import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.profile.entity.Sex;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 진료실 전달(S4)과 공유 링크(S6)의 응답. */
public final class HandoffDtos {

    private HandoffDtos() {}

    // ---------- 전달 화면 ----------

    /**
     * 의사에게 보여주는 카드.
     *
     * <p>{@code CardResponse} 를 그대로 쓰지 않는다. 거기엔 {@code promptVersion},
     * {@code modelId}, {@code aiRequestId}, {@code sessionId}, {@code completeness} 가
     * 들어 있는데 <b>의사에게 AI 파이프라인 버전을 보여줄 이유가 없다</b>.
     * 낭독 모드 큰 글자 화면에 쓸 것만 남긴다.
     *
     * <p><b>{@code evidence} 는 남긴다.</b> 처음엔 내부 추적값이라 뺐는데, 의사가
     * "정말 저렇게 말했나"를 확인하는 근거라 오히려 진료실에서 필요한 값이다.
     * {@code source} 도 함께 낸다 — 환자가 나중에 고친 값인지 구별돼야 한다.
     *
     * <p>{@code status} 는 카드 축의 <b>5값</b>이다. 프로필 필드의 3값과 다르다.
     * {@code not_asked}("아직 안 물어봤다")와 {@code unknown}("모르겠다고 했다")이
     * 구분돼야 의사가 다시 물을지 판단할 수 있다.
     */
    public record HandoffView(
            Patient patient,
            String title,
            String chiefComplaint,
            Map<String, Axis> axes,
            List<String> redFlags,
            List<String> patientNotes,
            List<String> questions,
            DepartmentGuidance departmentGuidance,
            Instant confirmedAt
    ) {
        public record Patient(String name, Integer age, Sex sex) {}

        public record Axis(AxisStatus status, String value, List<String> evidence, AxisSource source) {}

        /** <b>배열이다.</b> 하나로 좁히지 않는다 — 좁히는 순간 그게 추천이 된다. */
        public record DepartmentGuidance(List<String> departments, String source) {}

        public static HandoffView from(BriefingCard c) {
            Map<String, Axis> axes = new LinkedHashMap<>();
            c.axesByName().forEach((name, a) -> axes.put(name,
                    new Axis(a.getStatus(), a.getValue(), List.copyOf(a.getEvidence()), a.getSource())));

            return new HandoffView(
                    new Patient(c.getPatientName(), c.getPatientAge(), c.getPatientSex()),
                    c.displayTitle(),
                    c.getChiefComplaint(),
                    axes,
                    List.copyOf(c.getRedFlags()),
                    List.copyOf(c.getPatientNotes()),
                    List.copyOf(c.getQuestions()),
                    new DepartmentGuidance(
                            List.copyOf(c.getDepartmentGuidance()), c.getDepartmentGuidanceSource()),
                    c.getConfirmedAt());
        }
    }

}
