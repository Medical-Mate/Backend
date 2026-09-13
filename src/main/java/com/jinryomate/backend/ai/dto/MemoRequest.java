package com.jinryomate.backend.ai.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * 메모 분류 요청. AI 계약의 {@code MemoRequest} 를 우리 말로 옮긴 것.
 *
 * <p><b>값이 여섯이라 따로 묶었다.</b> 인자로 늘어놓으면 {@code String} 이 둘, 참조형이
 * 넷이라 순서를 틀려도 컴파일러가 못 잡는다.
 *
 * <p>세 가지로 쓰인다.
 *
 * <table border="1">
 *   <caption>호출 형태</caption>
 *   <tr><th>언제</th><th>{@code labels}</th><th>{@code classify}</th><th>LLM</th></tr>
 *   <tr><td>서버가 나눈다 (기본)</td><td>없음</td><td>{@code null}</td><td>쓴다</td></tr>
 *   <tr><td>문장만 나눈다 (온디바이스 1단계)</td><td>없음</td><td>{@code false}</td><td>안 쓴다</td></tr>
 *   <tr><td>라벨로 조립 (수정 · 온디바이스 3단계)</td><td>있음</td><td>무시</td><td>안 쓴다</td></tr>
 * </table>
 *
 * @param classify   {@code null} 이면 우리가 정한다 — 라벨이 있으면 {@code false}.
 *                   {@code false} 를 명시하면 <b>문장만</b> 나눠 받는다
 * @param labelsMeta 폰이 라벨을 붙였을 때 무엇으로 붙였는지. 서버가 나눌 때는 {@code null}
 */
public record MemoRequest(
        String memo,
        LocalDate visitedOn,
        String clinicName,
        Map<String, String> labels,
        Boolean classify,
        LabelsMeta labelsMeta
) {

    /** 서버가 나누는 기본 호출. */
    public static MemoRequest classifyOnServer(String memo, LocalDate visitedOn, String clinicName) {
        return new MemoRequest(memo, visitedOn, clinicName, null, null, null);
    }

    /** 라벨이 있으면 조립만 한다. 없을 때만 {@code classify} 가 뜻을 갖는다. */
    public boolean hasLabels() {
        return labels != null && !labels.isEmpty();
    }

    /** AI 에 실제로 보낼 {@code classify} 값. */
    public boolean effectiveClassify() {
        if (hasLabels()) {
            return false;
        }
        return classify == null || classify;
    }
}
