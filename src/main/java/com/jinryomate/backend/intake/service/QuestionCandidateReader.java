package com.jinryomate.backend.intake.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.dto.AiCard;
import com.jinryomate.backend.intake.dto.IntakeDtos.QuestionCandidate;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 세션에 보관해둔 AI 카드에서 질문 후보만 꺼낸다. 화면 {@code 1i} 가 쓴다.
 *
 * <p><b>카드가 아니라 세션에서 읽는다.</b> 화면 순서가
 * {@code 문답 → 통증 강도 → 물어볼 것 → 카드} 라, 후보가 필요한 시점에는 카드 행이 아직 없다.
 *
 * <p><b>후보는 저장하지 않는다.</b> 환자가 고르고 나면 최종 목록만 남으면 되고, 고르지 않은
 * 후보를 들고 있을 이유가 없다. 원문은 세션의 {@code aiCard} 안에 이미 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionCandidateReader {

    /** 화면 {@code 1i} 의 "적어둔 질문 · 최대 3개". */
    private static final int MAX = 3;

    private final ObjectMapper objectMapper;

    public List<QuestionCandidate> read(String aiCardJson) {
        if (aiCardJson == null || aiCardJson.isBlank()) {
            return List.of();
        }

        AiCard card;
        try {
            card = objectMapper.readValue(aiCardJson, AiCard.class);
        } catch (Exception e) {
            // 후보는 덤이다. 못 읽는다고 세션 조회를 실패시키면 화면이 통째로 안 뜬다.
            log.warn("질문 후보를 읽을 수 없습니다: {}", e.getClass().getSimpleName());
            return List.of();
        }

        if (card.questionCandidates() == null) {
            return List.of();
        }

        // 배열 순서가 아니라 rank 로 정렬한다. rank 가 없는 것은 뒤로 보낸다.
        return card.questionCandidates().stream()
                .filter(c -> c != null && c.text() != null && !c.text().isBlank())
                .sorted(Comparator.comparing(
                        AiCard.QuestionCandidate::rank,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX)
                .map(c -> new QuestionCandidate(c.text(), c.source(), c.rank()))
                .toList();
    }
}
