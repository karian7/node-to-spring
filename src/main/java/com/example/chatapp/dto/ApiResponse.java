package com.example.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private List<ValidationError> errors;
    private String code;
    private String stack;
    private String path;
    private Map<String, Object> meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }

    @Deprecated
    public static <T> ApiResponse<T> error(String message, Map<String, Object> details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .meta(details)
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(errorCode.getMessage())
                .code(errorCode.getCode())
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, String customMessage) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(customMessage)
                .code(errorCode.getCode())
                .build();
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, Map<String, Object> details) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(errorCode.getMessage())
                .code(errorCode.getCode())
                .meta(details)
                .build();
    }

    public static <T> ApiResponse<T> validationError(List<ValidationError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .errors(errors)
                .code(ApiErrorCode.VALIDATION_ERROR.getCode())
                .build();
    }

    public static <T> ApiResponse<T> validationError(String message, List<ValidationError> errors) {
        ApiResponse<T> response = validationError(errors);
        response.setMessage(message);
        return response;
    }
}
