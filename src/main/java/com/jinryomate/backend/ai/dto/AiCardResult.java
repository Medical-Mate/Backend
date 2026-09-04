package com.jinryomate.backend.ai.dto;

import com.jinryomate.backend.card.entity.CardContent;

/**
 * AI 서비스가 돌려준 카드와 추적 정보.
 *
 * @param content         카드 본문. <b>아직 검증 전이다</b>
 * @param pipelineVersion 어떤 파이프라인이 만들었는지. 카드와 함께 저장한다
 * @param requestId       장애 시 AI 쪽 로그와 잇는 열쇠
 */
public record AiCardResult(CardContent content, String pipelineVersion, String requestId) {}
