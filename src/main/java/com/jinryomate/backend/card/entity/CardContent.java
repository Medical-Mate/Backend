package com.jinryomate.backend.card.entity;

import java.util.List;
import java.util.Map;

/**
 * 카드 본문. 엔티티에 넣기 전의 검증된 값 묶음이다.
 *
 * <p>AI 가 준 카드도, 환자가 S3 에서 고친 값도 결국 이 형태로 모여 엔티티에 들어간다.
 * 검증({@link com.jinryomate.backend.card.service.CardContentValidator})을 통과한 것만
 * 이 타입으로 만들어진다.
 *
 * <p><b>복용약·알레르기가 없다.</b> 그 둘은 프로필에 있고, 카드에 또 두면 같은 사실이 두 곳에
 * 남아 어긋난다. 카드 화면에는 프로필 값을 얹어 보여준다.
 *
 * @param axes                축 이름 → 축. 개수를 고정하지 않는다 — AI 가 축을 늘려도 깨지지 않아야 한다
 * @param departmentGuidance  진료과 안내. <b>배열이다.</b> 하나로 좁히는 순간 그게 추천이 된다.
 *                            진료과가 없는 부위 14곳에서는 빈 목록이다
 */
public record CardContent(
        String title,
        String chiefComplaint,
        Map<String, CardAxis> axes,
        List<String> redFlags,
        List<String> patientNotes,
        List<String> questions,
        List<String> departmentGuidance,
        String departmentGuidanceSource,
        Double completeness,
        Boolean minimallyComplete
) {}
