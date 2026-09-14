package com.jinryomate.backend.global.error;

import com.jinryomate.backend.global.web.RequestIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return build(code, e.getMessage(), e.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getDefaultMessage());
        log.warn("[INVALID_REQUEST] {}", message);
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 쿼리·경로 값이 제약을 어겼을 때. {@code @Validated} 가 붙은 컨트롤러에서 나온다.
     *
     * <p>본문 검증({@code MethodArgumentNotValidException})과 <b>다른 예외다.</b> 스프링이
     * 던지는 것이 아니라 Jakarta 검증이 던져서, 본문 쪽 핸들러로는 안 잡힌다. 이것도 없으면
     * catch-all 을 타고 500 이 된다 — #44 에서 고친 것과 같은 부류다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse(ErrorCode.INVALID_REQUEST.getDefaultMessage());
        log.warn("[INVALID_REQUEST] {}", message);
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 본문을 읽지 못했을 때. 깨진 JSON, 잘못된 인코딩, 타입이 안 맞는 값 등.
     *
     * <p><b>예외 메시지를 응답에 담지 않는다.</b> 파싱 실패 메시지에는 본문 일부가 섞이는데,
     * 이 API 에서 그 본문은 증상 텍스트다. 로그에도 예외 타입만 남긴다.
     * 원인을 짚어야 하면 {@code meta.requestId} 로 로그를 찾는다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("[INVALID_REQUEST] 본문을 읽지 못했습니다: {}", e.getClass().getSimpleName());
        return build(ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없습니다. 형식을 확인해주세요.");
    }

    /** 경로·쿼리 값의 타입이 안 맞을 때. 예: {@code /api/cards/abc}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "'" + e.getName() + "' 값의 형식이 올바르지 않습니다.";
        log.warn("[INVALID_REQUEST] {}", message);
        return build(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 필수 쿼리 파라미터가 빠졌을 때.
     *
     * <p>지금은 필수 파라미터를 쓰는 엔드포인트가 없어 걸릴 일이 없다. 그래도 남긴다 —
     * 앞으로 하나라도 생기면 500 이 아니라 400 으로 나가야 한다.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(Exception e) {
        log.warn("[INVALID_REQUEST] 필수 값 누락: {}", e.getClass().getSimpleName());
        return build(ErrorCode.INVALID_REQUEST, "필수 값이 빠졌습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("[METHOD_NOT_ALLOWED] {}", e.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
    }

    /**
     * 요청 {@code Content-Type} 을 읽을 수 없을 때. 예: JSON 엔드포인트에 {@code text/plain}.
     *
     * <p><b>예외 메시지를 응답에 담지 않는다.</b> 미디어 타입 예외에 본문이 섞이지는 않지만,
     * {@code HttpMessageNotReadableException} 과 같은 규칙을 두는 편이 낫다 — 예외를 하나
     * 두면 다음 사람이 어디까지 안전한지 매번 판단해야 한다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        log.warn("[UNSUPPORTED_MEDIA_TYPE] {}", e.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage());
    }

    /** {@code Accept} 로 요청한 형식을 만들 수 없을 때. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException e) {
        log.warn("[NOT_ACCEPTABLE] {}", e.getSupportedMediaTypes());
        return build(ErrorCode.NOT_ACCEPTABLE, ErrorCode.NOT_ACCEPTABLE.getDefaultMessage());
    }

    /** 매핑된 핸들러가 없을 때. 오타 난 경로가 500 으로 보이면 앱이 서버 장애로 오해한다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getDefaultMessage());
    }

    /**
     * 여기까지 온 것은 우리 잘못이다.
     *
     * <p>클라이언트 잘못은 위에서 각각 걸러낸다. 그것까지 500 으로 섞이면
     * 운영에서 진짜 장애가 묻힌다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 증상 텍스트가 로그로 새지 않도록 예외 메시지는 남기되 요청 본문은 남기지 않는다.
        log.error("처리되지 않은 예외", e);
        return build(ErrorCode.INTERNAL, ErrorCode.INTERNAL.getDefaultMessage());
    }

    /**
     * 오류 응답을 만든다.
     *
     * <p><b>{@code Content-Type} 을 JSON 으로 못 박는다.</b> 그러지 않으면 {@code Accept} 가
     * 안 맞을 때(406) 본문이 통째로 비어 나간다 — 협상에 실패한 응답을 또 협상하기 때문이다.
     * 앱이 오류 분기를 하나만 두면 되도록 형태를 통일한 것이 이 프로젝트의 결정인데,
     * 하필 형식을 잘못 보낸 요청에서 그 형태가 깨지면 원인을 짚기가 제일 어렵다.
     */
    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return build(code, message, null);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message,
                                                java.util.Map<String, Object> details) {
        return ResponseEntity
                .status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(code, message, RequestIdFilter.current(), details));
    }
}
