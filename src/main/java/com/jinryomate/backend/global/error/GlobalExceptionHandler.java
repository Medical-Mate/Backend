package com.jinryomate.backend.global.error;

import com.jinryomate.backend.global.web.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] {}", code.name(), e.getMessage());
        return build(code, e.getMessage());
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

    /** 필수 파라미터나 multipart 파일이 빠졌을 때. */
    @ExceptionHandler({MissingServletRequestParameterException.class,
                       MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleMissingPart(Exception e) {
        log.warn("[INVALID_REQUEST] 필수 값 누락: {}", e.getClass().getSimpleName());
        return build(ErrorCode.INVALID_REQUEST, "필수 값이 빠졌습니다.");
    }

    /**
     * 업로드 용량 초과.
     *
     * <p>{@code AttachmentService} 에도 같은 검사가 있지만, 스프링의 multipart 한도가
     * 먼저 걸려 서비스 코드까지 오지 못한다. 여기서 같은 안내를 해야 앱이 쓸 수 있다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        log.warn("[INVALID_REQUEST] 업로드 용량 초과");
        return build(ErrorCode.INVALID_REQUEST, "사진은 한 장에 10MB까지 올릴 수 있습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
        log.warn("[METHOD_NOT_ALLOWED] {}", e.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getDefaultMessage());
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

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message) {
        return ResponseEntity
                .status(code.getStatus())
                .body(ErrorResponse.of(code, message, RequestIdFilter.current()));
    }
}
