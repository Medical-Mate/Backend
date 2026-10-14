package com.jinryomate.backend.intake.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.ai.dto.AiCard;
import com.jinryomate.backend.intake.dto.IntakeDtos.CardAxis;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 세션에 보관한 AI 카드에서 축 값·상태를 꺼낸다. 폰이 질문 후보를 만드는 재료다(#164).
 *
 * <p><b>AI 가 준 순서 그대로, 있는 축 그대로 낸다.</b> 우리가 8축 목록을 들고 거르면 AI 가
 * 축을 바꾸는 날 한쪽이 낡는다. 값도 다듬지 않는다 — 폰 프롬프트에 무엇을 넣을지는 앱이 정한다.
 *
 * <p>{@code evidence} 는 싣지 않는다. 후보를 만드는 데 필요 없고, 응답마다 환자 발화 원문을
 * 한 번 더 내보낼 이유가 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardAxisReader {

    private final ObjectMapper objectMapper;

    public List<CardAxis> read(String aiCardJson) {
        if (aiCardJson == null || aiCardJson.isBlank()) {
            return List.of();
        }
        AiCard card;
        try {
            card = objectMapper.readValue(aiCardJson, AiCard.class);
        } catch (Exception e) {
            // 후보 읽기와 같다 — 못 읽는다고 세션 조회를 실패시키면 화면이 통째로 안 뜬다.
            log.warn("카드 축을 읽을 수 없습니다: {}", e.getClass().getSimpleName());
            return List.of();
        }
        if (card.axes() == null) {
            return List.of();
        }
        return card.axes().entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> new CardAxis(e.getKey(), e.getValue().status(), e.getValue().value()))
                .toList();
    }
}
