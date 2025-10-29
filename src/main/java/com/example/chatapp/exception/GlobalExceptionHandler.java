package com.example.chatapp.exception;

import com.example.chatapp.dto.ApiErrorCode;
import com.example.chatapp.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기
 * Node.js backend와 동일한 에러 응답 형식 제공
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:production}")
    private String activeProfile;

    /**
     * 인증 예외 처리
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {

        log.warn("인증 실패: {} - {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ApiErrorCode.UNAUTHORIZED);
        return ResponseEntity.status(ApiErrorCode.UNAUTHORIZED.getHttpStatus()).body(response);
    }

    /**
     * 인가 예외 처리 (권한 없음)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDeniedException(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("접근 권한 없음: {} - {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ApiErrorCode.FORBIDDEN);
        return ResponseEntity.status(ApiErrorCode.FORBIDDEN.getHttpStatus()).body(response);
    }

    /**
     * 유효성 검증 실패 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("유효성 검증 실패: {}", fieldErrors);

        ApiResponse<Object> response = ApiResponse.validationError(fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    /**
     * 파일 업로드 크기 초과 예외 처리
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex) {

        log.warn("파일 업로드 크기 초과: {}", ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(ApiErrorCode.FILE_TOO_LARGE);
        return ResponseEntity.status(ApiErrorCode.FILE_TOO_LARGE.getHttpStatus()).body(response);
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {

        log.warn("비즈니스 예외: {} - {} ({})",
                request.getRequestURI(), ex.getMessage(), ex.getErrorCode().getCode());

        ApiResponse<Object> response;
        if (ex.getDetails() != null) {
            response = ApiResponse.error(ex.getErrorCode(), ex.getDetails());
        } else {
            response = ApiResponse.error(ex.getErrorCode(), ex.getMessage());
        }

        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(response);
    }

    /**
     * 리소스 찾을 수 없음 예외 처리
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceNotFoundException(
            ResourceNotFoundException ex, HttpServletRequest request) {

        log.warn("리소스 없음: {} - {}", request.getRequestURI(), ex.getMessage());

        ApiResponse<Object> response = ApiResponse.error(
            ex.getErrorCode() != null ? ex.getErrorCode() : ApiErrorCode.RESOURCE_NOT_FOUND,
            ex.getMessage()
        );

        return ResponseEntity.notFound().build();
    }

    /**
     * 일반적인 Runtime 예외 처리
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(
            RuntimeException ex, HttpServletRequest request) {

        log.error("런타임 예외: {} - {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiResponse<Object> response;
        if ("development".equals(activeProfile)) {
            // 개발 환경에서는 상세 스택 트레이스 제공
            Map<String, Object> details = Map.of(
                "exception", ex.getClass().getSimpleName(),
                "message", ex.getMessage(),
                "stackTrace", ex.getStackTrace()
            );
            response = ApiResponse.error(ApiErrorCode.INTERNAL_SERVER_ERROR, details);
        } else {
            // 프로덕션 환경에서는 간단한 메시지만
            response = ApiResponse.error(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }

        return ResponseEntity.status(ApiErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }

    /**
     * 모든 예외의 최종 처리기
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("예상치 못한 예외: {} - {}", request.getRequestURI(), ex.getMessage(), ex);

        ApiResponse<Object> response;
        if ("development".equals(activeProfile)) {
            Map<String, Object> details = Map.of(
                "exception", ex.getClass().getSimpleName(),
                "message", ex.getMessage(),
                "cause", ex.getCause() != null ? ex.getCause().getMessage() : "없음"
            );
            response = ApiResponse.error(ApiErrorCode.INTERNAL_SERVER_ERROR, details);
        } else {
            response = ApiResponse.error(ApiErrorCode.INTERNAL_SERVER_ERROR);
        }

        return ResponseEntity.status(ApiErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).body(response);
    }
}
