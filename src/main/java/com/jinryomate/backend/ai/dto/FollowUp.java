package com.jinryomate.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;

/**
 * 재방문 시점. AI 계약의 {@code follow_up_date} 다.
 *
 * <p><b>날짜 하나가 아니다.</b> 환자가 들은 말은 "2주 뒤" 이고, 그걸 날짜로 바꾼 것이
 * {@code date} 이며, 그 날짜가 정확한 지정이 아니라는 표시가 {@code approximate} 다.
 * 시안 {@code 1q-1} 은 셋을 합쳐 <b>{@code 2주 뒤 (9월 27일 전후)}</b> 로 찍는다.
 *
 * <p>날짜 계산은 AI 가 결정론으로 한다(LLM 이 아니다). 계약의 {@code basis} 는
 * {@code "visit_date 2026-09-13 + 14d"} 같은 계산 근거인데, 화면에 쓰는 곳이 없어 싣지 않는다.
 *
 * @param date        계산된 날짜. AI 가 못 읽으면 {@code null} — 그때는 앱 달력에서 직접 고른다
 * @param text        환자가 말한 그대로. {@code 2주 뒤}
 * @param approximate {@code true} 면 화면에 "전후" 를 붙인다
 */
public record FollowUp(LocalDate date, String text, boolean approximate) {

    /** 재방문 얘기가 아예 없었을 때. */
    public static final FollowUp NONE = new FollowUp(null, null, false);

    /**
     * 응답에 나가지 않는다.
     *
     * <p>{@code isEmpty} 라 Jackson 이 이걸 {@code empty} 라는 필드로 읽어 응답에 실었다.
     * 우리 안에서만 쓰는 판단이라 앱이 볼 이유가 없다.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return date == null && (text == null || text.isBlank());
    }
}
