package com.jinryomate.backend.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 무엇이 문장에 라벨을 붙였나. AI 계약의 {@code labels_meta} · {@code provenance}.
 *
 * <p>서버가 나눴으면 {@code memo-small-v4} / {@code apac.amazon.nova-pro-v1:0} 같은 값이고,
 * 폰이 나눴으면 {@code small-v4} / {@code Qwen3-1.7B-Q4_0} 이 온다.
 *
 * <p><b>폰이 붙였을 때는 앱이 알려줘야 한다.</b> 서버는 라벨만 받으므로 무엇이 만들었는지
 * 알 길이 없다. 안 보내면 AI 가 {@code client} 로 뭉뚱그려 기록한다 — 나중에 "어느 버전이
 * 이상하게 나눴나"를 못 되짚는다.
 *
 * <p>AI 쪽 {@code ExtractionMeta} 가 {@code additionalProperties: false} 라 필드 둘뿐이다.
 * 더 보내면 422 다.
 */
public record LabelsMeta(String modelId, String promptVersion) {

    /**
     * 응답에 나가지 않는다.
     *
     * <p>{@code isEmpty} 라 Jackson 이 {@code empty} 라는 필드로 읽어 응답에 실었다.
     * {@link FollowUp} 에서 같은 것을 고쳐 놓고 여기서 되풀이했다 — 파생 메서드를
     * 붙일 때마다 응답에 새는지 봐야 한다.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return (modelId == null || modelId.isBlank())
                && (promptVersion == null || promptVersion.isBlank());
    }
}
