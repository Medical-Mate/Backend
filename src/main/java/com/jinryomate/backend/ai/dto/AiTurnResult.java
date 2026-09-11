package com.jinryomate.backend.ai.dto;

/**
 * 문답 한 턴의 결과.
 *
 * <p>AI 계약({@code docs/api-previsit.md})의 {@code POST /v1/previsit/turns} 응답을 담는다.
 *
 * @param reply     환자에게 보여줄 다음 문장. 질문이거나 마무리 인사다
 * @param ended     문답이 끝났는지. <b>true 여도 종료를 강제하지 않는다</b> —
 *                  환자가 언제 나가도 그때까지의 내용이 결과다
 * @param endReason {@code stop} · {@code complete} · {@code max_turns} · {@code budget}.
 *                  끝나지 않았으면 {@code null}
 * @param state     다음 턴에 그대로 실어 보낼 값. <b>열어보지 않는다.</b>
 *                  AI 계약이 불투명하게 다루라고 명시했고, 내부 구조에 의존하는 순간
 *                  AI 쪽 변경이 우리를 깨뜨린다
 */
public record AiTurnResult(
        String reply,
        boolean ended,
        String endReason,
        String state
) {}
