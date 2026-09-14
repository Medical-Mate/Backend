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
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 방식입니다."),

    /** 요청 {@code Content-Type} 을 서버가 읽지 못한다. 이 API 는 JSON 만 받는다. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 형식입니다. JSON으로 보내주세요."),

    /** {@code Accept} 로 요청한 형식을 서버가 만들지 못한다. */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "요청하신 형식으로는 응답할 수 없습니다."),

    /**
     * 바깥 서비스가 응답하지 못한다. 병원 검색(심평원)이 이 자리다.
     *
     * <p><b>빈 목록으로 감추지 않는다.</b> 앱은 "그런 병원 없음"과 "상류가 죽음"을 구별해야
     * 한다 — 사용자에게 하는 말이 "검색 결과 없음"과 "잠시 뒤 다시"로 갈린다.
     */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "잠시 후 다시 시도해주세요."),

    /**
     * 메모를 나눈 규칙이 바뀌어서 보낸 라벨의 문장 번호를 믿을 수 없다.
     *
     * <p>라벨은 <b>문장 번호로 주소를 매깁니다.</b> AI 가 분리 규칙을 고치면 같은 메모가
     * 다른 개수·다른 번호로 나뉘는데, 그 배포가 1p(메모 작성)와 1q-2(라벨 수정) 사이에
     * 끼면 앱은 예전 번호로 매긴 라벨을 보내고 서버는 새 문장에 그 번호를 붙입니다.
     *
     * <p><b>이때 200 이 나가고 카드도 멀쩡해 보입니다.</b> 환자가 "약" 이라고 표시한 줄에
     * 검사 얘기가 들어가 있는데 아무도 모릅니다. 그래서 막습니다.
     *
     * <p>앱은 {@code labels} 없이 같은 메모를 다시 보내 새 문장과 번호를 받으면 됩니다.
     * 재시도로 풀리는 일이라 4xx 이고, 규칙과 라벨이 어긋난 상태 충돌이라 409 입니다.
     */
    SPLIT_VERSION_CHANGED(HttpStatus.CONFLICT,
            "메모를 나눈 방식이 바뀌었어요. 다시 정리해주세요."),

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
