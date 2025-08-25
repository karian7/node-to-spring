package com.example.chatapp.controller;

import com.example.chatapp.dto.ApiResponse;
import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.exception.ResourceNotFoundException;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * 메시지 시스템 완성 컨트롤러
 * Node.js backend의 메시지 API와 동일한 기능 제공
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    /**
     * 메시지 전송 (파일 첨부 지원)
     */
    @PostMapping
    public ResponseEntity<?> sendMessage(@Valid @RequestBody MessageRequest messageRequest, Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            MessageResponse savedMessage = messageService.createMessage(
                    messageRequest.getRoomId(),
                    messageRequest.getContent(),
                    messageRequest.getFileId(),
                    user.getId()
            );

            return ResponseEntity.ok(ApiResponse.success("메시지가 성공적으로 전송되었습니다.", savedMessage));

        } catch (Exception e) {
            log.error("메시지 전송 중 에러 발생", e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 전송 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    /**
     * 룸의 메시지 목록 조회 (페이지네이션)
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getRoomMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "30") Integer limit,
            Principal principal) {
        try {
            // TODO: 룸 참여자 권한 검증 추가

            var messagesResponse = messageService.getMessagesWithPagination(roomId, before, limit);
            return ResponseEntity.ok(ApiResponse.success("메시지를 성공적으로 조회했습니다.", messagesResponse));

        } catch (Exception e) {
            log.error("메시지 목록 조회 중 에러 발생: {}", roomId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 목록 조회 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 메시지 읽음 처리
     */
    @PostMapping("/{messageId}/read")
    public ResponseEntity<?> markMessageAsRead(
            @PathVariable String messageId,
            @RequestParam(required = false) String deviceId,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            messageService.markMessageAsRead(messageId, user.getId(), deviceId);
            return ResponseEntity.ok(ApiResponse.success("메시지를 읽음 처리했습니다."));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("메시지 읽음 처리 중 에러 발생: {}", messageId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 읽음 처리 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 룸의 모든 메시지 읽음 처리
     */
    @PostMapping("/room/{roomId}/read-all")
    public ResponseEntity<?> markAllMessagesAsRead(
            @PathVariable String roomId,
            @RequestParam(required = false) String deviceId,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            messageService.markAllMessagesAsRead(roomId, user.getId(), deviceId);
            return ResponseEntity.ok(ApiResponse.success("모든 메시지를 읽음 처리했습니다."));

        } catch (Exception e) {
            log.error("전체 메시지 읽음 처리 중 에러 발생: {}", roomId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("전체 메시지 읽음 처리 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 메시지 수정
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<?> editMessage(
            @PathVariable String messageId,
            @RequestBody MessageRequest messageRequest,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            MessageResponse updatedMessage = messageService.editMessage(
                    messageId, messageRequest.getContent(), user.getId());

            return ResponseEntity.ok(ApiResponse.success("메시지가 성공적으로 수정되었습니다.", updatedMessage));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("메시지 수정 중 에러 발생: {}", messageId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 수정 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable String messageId, Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            messageService.deleteMessage(messageId, user.getId());
            return ResponseEntity.ok(ApiResponse.success("메시지가 성공적으로 삭제되었습니다."));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("메시지 삭제 중 에러 발생: {}", messageId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 삭제 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    /**
     * 메시지 핀 설정/해제
     */
    @PostMapping("/{messageId}/pin")
    public ResponseEntity<?> toggleMessagePin(@PathVariable String messageId, Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            MessageResponse pinnedMessage = messageService.toggleMessagePin(messageId, user.getId());

            String action = pinnedMessage.isPinned() ? "고정" : "고정 해제";
            return ResponseEntity.ok(ApiResponse.success(
                    "메시지가 성공적으로 " + action + "되었습니다.", pinnedMessage));

        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("메시지 핀 처리 중 에러 발생: {}", messageId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("메시지 핀 처리 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    @GetMapping("/room/{roomId}/unread-count")
    public ResponseEntity<?> getUnreadMessageCount(@PathVariable String roomId, Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            long unreadCount = messageService.getUnreadMessageCount(roomId, user.getId());
            return ResponseEntity.ok(ApiResponse.success("읽지 않은 메시지 수를 조회했습니다.",
                    Map.of("roomId", roomId, "unreadCount", unreadCount)));

        } catch (Exception e) {
            log.error("읽지 않은 메시지 수 조회 중 에러 발생: {}", roomId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("읽지 않은 메시지 수 조회 중 오류가 발생했습니다.")
            );
        }
    }
}
