package com.jinryomate.backend.card.entity;

import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.entity.Sex;
import java.util.List;

/**
 * 카드를 만들 때 박아두는 환자 정보.
 *
 * <p><b>카드는 참조가 아니라 스냅샷이다.</b> 프로필을 나중에 고쳐도 의사가 본 카드는 그대로여야
 * 한다. 9월에 만든 카드를 12월에 열었을 때 그때 복용 중인 약이 붙으면 안 된다 — 진료실에서
 * 맞는 값은 <b>"이 카드를 만들 당시 이 환자가 먹던 약"</b>이다.
 *
 * <p>앱이 프로필 API 를 따로 읽어 얹는 방법도 있지만 그러면 <b>보는 시점의 값</b>이 찍힌다.
 * 그건 서버만 막을 수 있다.
 *
 * <p>값이 아홉 개라 따로 묶었다. 인자로 늘어놓으면 순서를 틀려도 컴파일러가 못 잡는다 —
 * 같은 타입(`FieldStatus`)이 셋이고 `List<String>` 이 둘이다.
 */
public record PatientSnapshot(
        String name,
        Integer age,
        Sex sex,

        FieldStatus medicationsStatus,
        List<String> medications,

        FieldStatus conditionsStatus,
        List<String> conditions,

        /** <b>카드 밖 경고 자리에 찍힌다</b> — "처방 전에 꼭 확인해 주세요". */
        FieldStatus allergiesStatus,
        String allergiesText
) {

    /** 프로필이 아직 없으면 빈 스냅샷. 문답은 나이·성별만 있으면 시작되므로 있을 수 있다. */
    public static PatientSnapshot from(HealthProfile profile) {
        if (profile == null) {
            return new PatientSnapshot(null, null, null, null, List.of(), null, List.of(), null, null);
        }
        return new PatientSnapshot(
                profile.getName(),
                profile.age(),
                profile.getSex(),
                profile.getMedicationsStatus(),
                List.copyOf(profile.getMedications()),
                profile.getConditionsStatus(),
                List.copyOf(profile.getConditions()),
                profile.getAllergiesStatus(),
                profile.getAllergies());
    }
}
