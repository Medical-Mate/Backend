package com.jinryomate.backend.ai.dto;

/**
 * AI가 만든 되묻기 문항 하나.
 *
 * @param question       환자에게 물을 말. 예: {@code 약은 하루 몇 번 드시면 되죠?}
 * @param expectedAnswer 정정 문구의 근거. 예: {@code 두 번이에요. 아침저녁 식후로.}
 */
public record ComprehensionQuestion(String question, String expectedAnswer) {}
