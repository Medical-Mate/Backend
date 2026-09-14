package com.jinryomate.backend.global.error;

import java.util.Map;

/**
 * 모든 오류 응답의 공통 형태.
 *
 * <pre>
 * {
 *   "error": { "code": "UPSTREAM_TIMEOUT", "message": "...", "retryable": true, "details": null },
 *   "meta":  { "requestId": "req_8f21c4" }
 * }
 * </pre>
 */
public record ErrorResponse(Error error, Meta meta) {

    /**
     * @param details 앱이 <b>다음 수를 두는 데 필요한 값</b>. 대부분 {@code null} 이다.
     *                오류를 보여주는 것 말고 할 일이 있을 때만 채운다 —
     *                {@code CARD_ALREADY_EDITED} 의 {@code latestCardId} 가 그렇다.
     *                <p><b>도메인 필드를 여기 직접 박지 않는다.</b> 전역 봉투라
     *                오류 종류마다 필드가 늘면 앱의 파싱이 오류마다 갈라진다.
     */
    public record Error(String code, String message, boolean retryable,
                        Map<String, Object> details) {}

    public record Meta(String requestId) {}

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return of(code, message, requestId, null);
    }

    public static ErrorResponse of(ErrorCode code, String message, String requestId,
                                   Map<String, Object> details) {
        return new ErrorResponse(
                new Error(code.name(), message, code.isRetryable(), details),
                new Meta(requestId));
    }
}
