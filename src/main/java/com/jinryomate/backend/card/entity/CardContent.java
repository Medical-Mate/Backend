package com.jinryomate.backend.card.entity;

import com.jinryomate.backend.profile.entity.FieldStatus;
import java.util.List;
import java.util.Map;

/**
 * 카드 본문. 엔티티에 넣기 전의 검증된 값 묶음이다.
 *
 * <p>AI 응답도, 환자가 S3에서 고친 값도 결국 이 형태로 모여 엔티티에 들어간다.
 * 검증({@code CardContentValidator})을 통과한 것만 이 타입으로 만들어진다.
 */
public record CardContent(
        String title,

        FieldStatus onsetStatus,
        String onsetText,

        FieldStatus patternStatus,
        String patternText,

        FieldStatus siteStatus,
        String siteText,
        List<String> siteCodes,

        FieldStatus medicationsStatus,
        List<Medication> medications,

        FieldStatus allergiesStatus,
        String allergiesText,

        List<String> questions,
        Department suggestedDepartment,
        Map<String, List<Integer>> evidence
) {}
