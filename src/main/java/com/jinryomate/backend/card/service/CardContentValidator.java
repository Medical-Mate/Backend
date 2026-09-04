package com.jinryomate.backend.card.service;

import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.entity.Department;
import com.jinryomate.backend.card.entity.Medication;
import com.jinryomate.backend.profile.entity.FieldStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI가 준 카드를 저장 전에 검증한다.
 *
 * <p><b>걸린 필드를 버리되 카드 생성은 살린다.</b> 통째로 실패시키면 환자가 6문항을
 * 답한 문답이 그대로 날아간다. 걸린 필드는 {@link FieldStatus#UNKNOWN}으로 낮춰
 * 환자가 S3 화면에서 직접 채우게 한다.
 *
 * <p>규칙은 와이어프레임과 낭독 모드에서 나온 것이라 임의로 바꾸면 안 된다.
 */
@Slf4j
@Component
public class CardContentValidator {

    static final int TITLE_MAX = 20;
    static final int TEXT_MAX = 80;
    static final int QUESTION_MAX_COUNT = 3;
    static final int QUESTION_MAX_LENGTH = 40;

    /**
     * 카드에 찍히면 안 되는 표현.
     *
     * <p>카드는 진단서가 아니다. "이거 류마티스인가요?"에 AI가 답하지 않는 것과 같은 이유로,
     * 제목에 병명이 들어가면 환자가 진단으로 받아들인다.
     */
    private static final Pattern DIAGNOSIS_TERMS = Pattern.compile(
            "(류마티스|관절염|디스크|골절|암|염증|증후군|장애|질환|병증|염$|증$|" +
            "당뇨|고혈압|천식|위염|장염|폐렴|결핵|골다공증|통풍)");

    /** 검증 결과. 무엇이 걸렸는지 남겨 재요청 여부를 판단한다. */
    public record Result(CardContent content, List<String> rejectedFields) {
        public boolean hasRejection() {
            return !rejectedFields.isEmpty();
        }
    }

    public Result validate(CardContent raw) {
        List<String> rejected = new ArrayList<>();

        String title = validateTitle(raw.title(), rejected);

        Field onset = validateTextField("onset", raw.onsetStatus(), raw.onsetText(), rejected);
        Field pattern = validateTextField("pattern", raw.patternStatus(), raw.patternText(), rejected);
        Field site = validateTextField("site", raw.siteStatus(), raw.siteText(), rejected);
        Field allergies = validateTextField("allergies", raw.allergiesStatus(), raw.allergiesText(), rejected);

        List<String> questions = validateQuestions(raw.questions(), rejected);
        // 진료과는 enum 이라 여기 도달한 값은 이미 목록 안에 있다.
        // enum 밖의 문자열은 AI 응답을 읽는 단계에서 null 로 떨어진다.
        Department department = raw.suggestedDepartment();

        FieldStatus medicationsStatus = raw.medicationsStatus() == null
                ? FieldStatus.UNKNOWN : raw.medicationsStatus();
        List<Medication> medications = medicationsStatus == FieldStatus.KNOWN
                ? safeList(raw.medications()) : List.of();

        CardContent content = new CardContent(
                title,
                onset.status(), onset.text(),
                pattern.status(), pattern.text(),
                site.status(), site.text(), safeList(raw.siteCodes()),
                medicationsStatus, medications,
                allergies.status(), allergies.text(),
                questions,
                department,
                raw.evidence() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(raw.evidence()));

        if (!rejected.isEmpty()) {
            // 필드 이름만 남긴다. 값은 증상 텍스트라 로그에 남기지 않는다.
            log.warn("카드 검증에서 걸린 필드: {}", rejected);
        }
        return new Result(content, rejected);
    }

    private record Field(FieldStatus status, String text) {}

    /**
     * 제목은 비울 수 없다. 카드에 제목이 없으면 의사가 무엇을 보는지 알 수 없어서,
     * 걸리면 잘라서라도 남긴다.
     */
    private String validateTitle(String title, List<String> rejected) {
        if (title == null || title.isBlank()) {
            rejected.add("title");
            return "증상 정리";
        }
        String trimmed = title.trim();
        if (DIAGNOSIS_TERMS.matcher(trimmed).find()) {
            rejected.add("title(진단명)");
            return "증상 정리";
        }
        if (trimmed.length() > TITLE_MAX) {
            rejected.add("title(길이)");
            return trimmed.substring(0, TITLE_MAX);
        }
        return trimmed;
    }

    private Field validateTextField(String name, FieldStatus status, String text, List<String> rejected) {
        if (status == null) {
            return new Field(FieldStatus.UNKNOWN, null);
        }
        if (status != FieldStatus.KNOWN) {
            // "없어요"/"잘 모르겠어요"는 값이 없는 게 정상이다.
            return new Field(status, null);
        }
        if (text == null || text.isBlank()) {
            rejected.add(name);
            return new Field(FieldStatus.UNKNOWN, null);
        }
        String trimmed = text.trim();
        if (trimmed.length() > TEXT_MAX) {
            rejected.add(name + "(길이)");
            return new Field(FieldStatus.UNKNOWN, null);
        }
        return new Field(FieldStatus.KNOWN, trimmed);
    }

    /** 최대 3개, 각 40자. 넘치는 것은 버리고 통과한 것만 남긴다. */
    private List<String> validateQuestions(List<String> questions, List<String> rejected) {
        if (questions == null || questions.isEmpty()) {
            return List.of();
        }
        List<String> kept = new ArrayList<>();
        boolean dropped = false;
        for (String q : questions) {
            if (q == null || q.isBlank()) {
                dropped = true;
                continue;
            }
            String trimmed = q.trim();
            if (trimmed.length() > QUESTION_MAX_LENGTH) {
                dropped = true;
                continue;
            }
            if (kept.size() >= QUESTION_MAX_COUNT) {
                dropped = true;
                continue;
            }
            kept.add(trimmed);
        }
        if (dropped) {
            rejected.add("questions");
        }
        return List.copyOf(kept);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
