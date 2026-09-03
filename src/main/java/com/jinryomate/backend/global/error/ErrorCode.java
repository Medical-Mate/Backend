package com.jinryomate.backend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 오류 코드.
 *
 * <p>AI 서비스 인터페이스 문서의 에러 코드와 이름을 맞춰 둔다.
 * 장애 시 백엔드 로그와 AI 로그를 같은 코드로 대조하기 위함이다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),

    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 서비스 호출에 실패했습니다."),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 응답이 지연되고 있습니다."),

    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    /** 백엔드가 자동 재시도해도 되는 오류인지. */
    public boolean isRetryable() {
        return this == UPSTREAM_ERROR || this == UPSTREAM_TIMEOUT;
    }
}
