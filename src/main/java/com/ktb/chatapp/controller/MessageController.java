package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 메시지 시스템 REST API 컨트롤러
 *
 * - GET /api/message/rooms/:roomId/messages → 500 에러 (미구현)
 * - 모든 메시지 기능은 Socket.IO를 통해 제공됨
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/message")
public class MessageController {

    /**
     * 채팅방 메시지 조회 - 미구현 (500 반환)
     * 실제 메시지 조회는 Socket.IO의 'fetchPreviousMessages' 이벤트를 사용하세요.
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> loadMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") Integer limit,
            Principal principal) {
        log.debug("Message REST API called - returning 500 (not implemented, use Socket.IO)");
        
        return ResponseEntity.status(500).body(
                ApiResponse.error("미구현.")
        );
    }
}
