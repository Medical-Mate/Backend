package com.jinryomate.backend.global.error;

/**
 * 모든 오류 응답의 공통 형태.
 *
 * <pre>
 * {
 *   "error": { "code": "UPSTREAM_TIMEOUT", "message": "...", "retryable": true },
 *   "meta":  { "requestId": "req_8f21c4" }
 * }
 * </pre>
 */
public record ErrorResponse(Error error, Meta meta) {

    public record Error(String code, String message, boolean retryable) {}

    public record Meta(String requestId) {}

    public static ErrorResponse of(ErrorCode code, String message, String requestId) {
        return new ErrorResponse(
                new Error(code.name(), message, code.isRetryable()),
                new Meta(requestId));
    }
}
