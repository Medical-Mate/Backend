package com.jinryomate.backend.ai.dto;

import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.HealthProfile;
import java.util.List;

/**
 * 질문 후보를 만들 때만 AI 에 넘기는 건강정보.
 *
 * <p><b>종료 뒤 한 번만 보낸다.</b> 매 턴 실어 보내면 요청 본문에 건강정보가 남는 표면이
 * 1턴치에서 20턴치로 늘어난다 — 서버 로그와 에러 리포트까지 포함해서다. AI 쪽도 같은 이유로
 * {@code question_candidates: true} 가 아닌 턴에 오면 422 로 거부한다.
 *
 * <p><b>AI 는 저장하지 않는다.</b> 프롬프트 재료로 쓰고 버리며 카드에도 넣지 않는다.
 *
 * <p>이게 없으면 복용약·알러지 관련 질문이 <b>한 번도 안 나온다.</b> AI 쪽이 프롬프트를 v8 까지
 * 올려 복용약 질문 비율을 3% → 65% 로 만들었는데, 재료가 비어 있으면 그 규칙이 걸리지 않는다.
 * 와이어프레임 질문 자리 셋 중 하나가 약 질문이다.
 *
 * @param medications 복용약. {@code null} 이면 AI 요청에서 그 키를 뺀다
 * @param conditions  기저질환
 * @param allergies   알레르기. <b>우리 쪽은 자유 텍스트 한 줄</b>이라 원문을 원소 하나로 넣는다 —
 *                    쉼표로 쪼개면 {@code "페니실린 계열 전부"} 같은 걸 잘못 자른다
 */
public record PatientProfile(
        List<String> medications,
        List<String> conditions,
        List<String> allergies
) {

    /**
     * 프로필을 AI 가 읽을 모양으로 바꾼다.
     *
     * <p><b>3값을 2값으로 접어야 한다.</b> 계약에 {@code unknown} 자리가 없다.
     *
     * <ul>
     *   <li>{@code KNOWN} → 값 목록
     *   <li>{@code NONE}("없어요") → 빈 목록. 계약의 "적었는데 없음"과 같다
     *   <li>{@code UNKNOWN}("잘 모르겠어요") → <b>그 키를 뺀다.</b> 계약의 "온보딩 안 거침"과
     *       같은 자리인데, 둘을 구분할 방법이 계약에 없다. 빈 목록으로 접으면 "없다"가 되어
     *       <b>모르는 사람에게 확인 질문이 안 나간다</b> — 그게 더 나쁘다고 봤다
     * </ul>
     *
     * <p>세 항목이 모두 {@code UNKNOWN} 이면 {@code null} 을 돌려준다. 그러면 요청에
     * {@code patient_profile} 자체를 싣지 않는다.
     */
    public static PatientProfile from(HealthProfile profile) {
        if (profile == null) {
            return null;
        }

        List<String> medications = listOrNull(profile.getMedicationsStatus(), profile.getMedications());
        List<String> conditions = listOrNull(profile.getConditionsStatus(), profile.getConditions());
        List<String> allergies = textOrNull(profile.getAllergiesStatus(), profile.getAllergies());

        if (medications == null && conditions == null && allergies == null) {
            return null;
        }
        return new PatientProfile(medications, conditions, allergies);
    }

    private static List<String> listOrNull(FieldStatus status, List<String> values) {
        if (status == FieldStatus.UNKNOWN) {
            return null;
        }
        return status == FieldStatus.KNOWN && values != null ? List.copyOf(values) : List.of();
    }

    private static List<String> textOrNull(FieldStatus status, String text) {
        if (status == FieldStatus.UNKNOWN) {
            return null;
        }
        if (status != FieldStatus.KNOWN || text == null || text.isBlank()) {
            return List.of();
        }
        // 쪼개지 않는다. 환자가 쓴 한 줄을 그대로 넘긴다.
        return List.of(text);
    }
}
