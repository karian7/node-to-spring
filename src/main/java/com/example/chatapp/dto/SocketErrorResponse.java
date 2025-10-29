package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 웹소켓 에러 응답 표준 DTO
 * Node.js 백엔드와 일관된 에러 응답 구조 제공
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocketErrorResponse {
    private String code;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, Object> context;
}
