package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.ai.dto.LabelsMeta;
import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.ai.dto.MemoRequest;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메모 분류에 실제로 붙기 전까지 쓰는 임시 구현.
 *
 * <p><b>키워드로 나눈다.</b> 문장에 "약" 이 있으면 약, "검사" 가 있으면 검사 하는 식이다.
 * 실제 분류와 결과가 다르지만, 문장 쪼개기 → 축 배치 → 라벨 반환 → 라벨로 재조립하는
 * 왕복은 실제 경로로 돌아가므로 앱이 1p·1q-1 을 만들고 검증할 수 있다.
 *
 * <p><b>온디바이스 경로도 흉내 낸다.</b> {@code classify=false} 면 문장만 나눠 주고 축은
 * 비운다. 앱이 폰 모델을 붙이기 전에 3단계 왕복을 먼저 맞춰볼 수 있어야 한다.
 *
 * <p><b>재방문 시점은 뽑지 않는다.</b> "2주 뒤" 같은 표현을 날짜로 바꾸는 것은 흉내로 될
 * 일이 아니고, 틀린 날짜가 캘린더에 들어가면 환자가 진료를 놓친다. 항상 비어 있다.
 *
 * <p>빈 등록은 {@link com.jinryomate.backend.ai.AiClientConfig} 가 한다.
 */
public class StubAiMemoClient implements AiMemoClient {

    /** AI 계약의 네 축. 순서까지 실제와 같게 둔다. */
    private static final String FINDINGS = "findings";
    private static final String TESTS = "tests";
    private static final String MEDICATION = "medication_instructions";
    private static final String FOLLOW_UP = "follow_up";

    private static final List<String> ALL = List.of(FINDINGS, TESTS, MEDICATION, FOLLOW_UP);

    /** AI 가 라벨 없이 나누기만 했을 때 쓰는 표시. 실제 서버와 같은 값이다. */
    private static final String UNLABELED = "none";

    @Override
    public MemoClassification classify(MemoRequest request) {
        List<String> sentences = split(request.memo());

        // 문장만 나눠 달라는 호출. 축은 비우고 라벨은 "아직 안 붙었다"로 둔다.
        if (!request.effectiveClassify() && !request.hasLabels()) {
            Map<String, String> unlabeled = new LinkedHashMap<>();
            for (int i = 0; i < sentences.size(); i++) {
                unlabeled.put(String.valueOf(i), UNLABELED);
            }
            Map<String, CardAxis> empty = new LinkedHashMap<>();
            ALL.forEach(axis -> empty.put(axis, CardAxis.notAsked(axis)));

            return new MemoClassification(empty, sentences, unlabeled, List.copyOf(sentences),
                    FollowUp.NONE, "client-labels", "client");
        }

        Map<String, String> resolved = request.hasLabels() ? request.labels() : guess(sentences);

        Map<String, List<String>> byAxis = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String axis = resolved.get(String.valueOf(i));
            if (axis == null || UNLABELED.equals(axis)) {
                notes.add(sentences.get(i));
                continue;
            }
            byAxis.computeIfAbsent(axis, k -> new ArrayList<>()).add(sentences.get(i));
        }

        Map<String, CardAxis> axes = new LinkedHashMap<>();
        for (String axis : ALL) {
            List<String> hits = byAxis.get(axis);
            if (hits == null || hits.isEmpty()) {
                axes.put(axis, CardAxis.notAsked(axis));
                continue;
            }
            String value = String.join(" · ", hits);
            axes.put(axis, CardAxis.of(axis, AxisStatus.FILLED,
                    value.length() > 200 ? value.substring(0, 200) : value,
                    hits, AxisSource.AI_EXTRACTION));
        }

        return new MemoClassification(axes, sentences, resolved, notes, FollowUp.NONE,
                promptVersion(request), modelId(request));
    }

    /** 폰이 붙였다고 알려주면 그 값을 되돌려준다. 실제 AI 도 provenance 를 그렇게 적는다. */
    private String promptVersion(MemoRequest request) {
        LabelsMeta meta = request.labelsMeta();
        return meta == null || meta.isEmpty() ? "stub" : meta.promptVersion();
    }

    private String modelId(MemoRequest request) {
        LabelsMeta meta = request.labelsMeta();
        return meta == null || meta.isEmpty() ? "stub" : meta.modelId();
    }

    /** 마침표로만 쪼갠다. 실제 AI 는 더 잘하지만 여기서 흉내 낼 값어치가 없다. */
    private List<String> split(String memo) {
        List<String> sentences = new ArrayList<>();
        for (String raw : memo.split("(?<=[.!?])\s+|\n+")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private Map<String, String> guess(List<String> sentences) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < sentences.size(); i++) {
            String s = sentences.get(i);
            String axis = null;
            if (s.contains("약") || s.contains("드시") || s.contains("먹")) {
                axis = MEDICATION;
            } else if (s.contains("검사") || s.contains("촬영") || s.contains("사진")) {
                axis = TESTS;
            } else if (s.contains("다음") || s.contains("오라") || s.contains("뒤에")) {
                axis = FOLLOW_UP;
            } else if (s.contains("래요") || s.contains("하셨")) {
                axis = FINDINGS;
            }
            if (axis != null) {
                labels.put(String.valueOf(i), axis);
            }
        }
        return labels;
    }
}
