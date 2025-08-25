package com.example.chatapp.exception;

import com.example.chatapp.dto.ApiErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * 비즈니스 로직 예외 클래스
 * Node.js backend의 비즈니스 예외와 동일한 구조 제공
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ApiErrorCode errorCode;
    private final Map<String, Object> details;

    public BusinessException(ApiErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ApiErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ApiErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public BusinessException(ApiErrorCode errorCode, String customMessage, Map<String, Object> details) {
        super(customMessage);
        this.errorCode = errorCode;
        this.details = details;
    }

    // 편의 메서드들
    public static BusinessException userNotFound() {
        return new BusinessException(ApiErrorCode.USER_NOT_FOUND);
    }

    public static BusinessException roomNotFound() {
        return new BusinessException(ApiErrorCode.ROOM_NOT_FOUND);
    }

    public static BusinessException wrongPassword() {
        return new BusinessException(ApiErrorCode.WRONG_PASSWORD);
    }

    public static BusinessException duplicateEmail() {
        return new BusinessException(ApiErrorCode.DUPLICATE_EMAIL);
    }

    public static BusinessException notRoomMember() {
        return new BusinessException(ApiErrorCode.NOT_ROOM_MEMBER);
    }

    public static BusinessException fileUploadFailed(String reason) {
        return new BusinessException(ApiErrorCode.FILE_UPLOAD_FAILED,
            "파일 업로드에 실패했습니다: " + reason);
    }
}
