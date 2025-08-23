package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private ApiError error;
    private LocalDateTime timestamp;

    // 성공 응답 생성 메서드들
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 에러 응답 생성 메서드들 (Node.js와 동일한 형식)
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(errorCode.getMessage())
                .error(ApiError.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .httpStatus(errorCode.getStatusCode())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, String customMessage) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(customMessage)
                .error(ApiError.builder()
                        .code(errorCode.getCode())
                        .message(customMessage)
                        .httpStatus(errorCode.getStatusCode())
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, Map<String, Object> details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(errorCode.getMessage())
                .error(ApiError.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getMessage())
                        .httpStatus(errorCode.getStatusCode())
                        .details(details)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 유효성 검증 에러를 위한 특별 메서드
    public static <T> ApiResponse<T> validationError(Map<String, String> fieldErrors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message("입력값 검증에 실패했습니다.")
                .error(ApiError.builder()
                        .code(ApiErrorCode.VALIDATION_ERROR.getCode())
                        .message("입력값 검증에 실패했습니다.")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .details(Map.of("fieldErrors", fieldErrors))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Node.js 호환을 위한 레거시 메서드
    @Deprecated
    public static <T> ApiResponse<T> error(String message, Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .error(ApiError.builder()
                        .code("LEGACY_ERROR")
                        .message(message)
                        .details(errors instanceof Map ? (Map<String, Object>) errors : Map.of("error", errors))
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 에러 정보를 담는 내부 클래스
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
        private Integer httpStatus;
        private Map<String, Object> details;
    }
}
