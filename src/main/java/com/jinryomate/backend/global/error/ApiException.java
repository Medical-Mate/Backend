package com.jinryomate.backend.global.error;

import java.util.Map;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 앱이 다음 수를 두는 데 필요한 값. 대부분 {@code null} 이다.
     *
     * <p>오류를 보여주는 것 말고 <b>할 일이 있을 때만</b> 채운다.
     * {@code CARD_ALREADY_EDITED} 가 최신 {@code cardId} 를 실어 보내는 자리다.
     */
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, null);
    }

    public ApiException(ErrorCode errorCode, String message, Map<String, Object> details) {
        this(errorCode, message, null, details);
    }

    private ApiException(ErrorCode errorCode, String message, Throwable cause,
                         Map<String, Object> details) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }
}
