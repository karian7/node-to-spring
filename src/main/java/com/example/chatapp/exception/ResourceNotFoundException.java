package com.example.chatapp.exception;

import com.example.chatapp.dto.ApiErrorCode;
import lombok.Getter;

/**
 * 리소스 찾을 수 없음 예외 클래스
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ResourceNotFoundException(ApiErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(ApiErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = ApiErrorCode.RESOURCE_NOT_FOUND;
    }

    // 편의 메서드들
    public static ResourceNotFoundException user(String userId) {
        return new ResourceNotFoundException(ApiErrorCode.USER_NOT_FOUND,
            "사용자를 찾을 수 없습니다: " + userId);
    }

    public static ResourceNotFoundException room(String roomId) {
        return new ResourceNotFoundException(ApiErrorCode.ROOM_NOT_FOUND,
            "채팅방을 찾을 수 없습니다: " + roomId);
    }

    public static ResourceNotFoundException message(String messageId) {
        return new ResourceNotFoundException(ApiErrorCode.MESSAGE_NOT_FOUND,
            "메시지를 찾을 수 없습니다: " + messageId);
    }

    public static ResourceNotFoundException file(String fileId) {
        return new ResourceNotFoundException(ApiErrorCode.FILE_NOT_FOUND,
            "파일을 찾을 수 없습니다: " + fileId);
    }
}
