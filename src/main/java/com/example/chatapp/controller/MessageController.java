package com.example.chatapp.controller;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.*;
import com.example.chatapp.service.FileService;
import com.example.chatapp.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class MessageController {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final FileService fileService;
    private final MessageService messageService; // 새로 추가된 MessageService
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Node.js와 동일한 배치 로딩 방식으로 메시지 조회
     */
    @GetMapping("/load")
    public ResponseEntity<ApiResponse<MessageService.MessageLoadResult>> loadMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "30") int limit,
            Principal principal) {

        try {
            // 방 존재 확인
            roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            // 사용자 정보 조회
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 배치 로딩으로 메시지 조회
            MessageService.MessageLoadResult result = messageService.loadMessages(
                    roomId, before, user.getId(), Math.min(limit, 50)); // 최대 50개로 제한

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error loading messages for room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 로딩 중 오류가 발생했습니다."));
        }
    }

    /**
     * 메시지를 읽음 상태로 표시
     */
    @PostMapping("/{messageId}/read")
    public ResponseEntity<ApiResponse<Void>> markMessageAsRead(
            @PathVariable String roomId,
            @PathVariable String messageId,
            Principal principal) {

        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            messageService.markMessageAsRead(messageId, user.getId());

            // WebSocket으로 읽음 상태 알림
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/read",
                Map.of("messageId", messageId, "userId", user.getId(), "readAt", LocalDateTime.now()));

            return ResponseEntity.ok(ApiResponse.success("메시지를 읽음 상태로 표시했습니다.", null));

        } catch (Exception e) {
            log.error("Error marking message as read: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("읽음 상태 업데이트 중 오류가 발생했습니다."));
        }
    }

    /**
     * 방의 모든 메시지를 읽음 상태로 표시
     */
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllMessagesAsRead(
            @PathVariable String roomId,
            Principal principal) {

        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            messageService.markAllMessagesInRoomAsRead(roomId, user.getId());

            // WebSocket으로 전체 읽음 상태 알림
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/read-all",
                Map.of("userId", user.getId(), "readAt", LocalDateTime.now()));

            return ResponseEntity.ok(ApiResponse.success("모든 메시지를 읽음 상태로 표시했습니다.", null));

        } catch (Exception e) {
            log.error("Error marking all messages as read in room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("읽음 상태 업데이트 중 오류가 발생했습니다."));
        }
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUnreadMessageCount(
            @PathVariable String roomId,
            Principal principal) {

        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            long unreadCount = messageService.getUnreadMessageCount(roomId, user.getId());

            Map<String, Object> result = Map.of(
                "roomId", roomId,
                "unreadCount", unreadCount
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error getting unread message count for room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("읽지 않은 메시지 수 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * WebSocket 메시지 전송 핸들러
     */
    @MessageMapping("/room/{roomId}/send")
    public void sendMessage(@DestinationVariable String roomId,
                           @Payload SendMessageRequest request,
                           Principal principal) {
        try {
            User sender = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            Message message = Message.builder()
                    .roomId(roomId)
                    .content(request.getContent())
                    .senderId(sender.getId())
                    .type(request.getType() != null ? request.getType() : MessageType.TEXT)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 파일이 포함된 경우 처리
            if (request.getFileId() != null) {
                message.setFileId(request.getFileId());
            }

            Message savedMessage = messageService.saveMessage(message);

            // WebSocket으로 실시간 메시지 전송
            MessageResponse response = mapToMessageResponse(savedMessage);
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/messages", response);

            log.debug("Message sent to room: {} by user: {}", roomId, sender.getId());

        } catch (Exception e) {
            log.error("Error sending message to room: {}", roomId, e);
            // 에러 메시지를 WebSocket으로 전송
            messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors",
                Map.of("error", "메시지 전송 중 오류가 발생했습니다."));
        }
    }

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessagesForRoom(@PathVariable String roomId, Pageable pageable) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

        Page<Message> messages = messageRepository.findByRoomId(roomId, pageable);
        Page<MessageResponse> messageResponses = messages.map(this::mapToMessageResponse);

        return ResponseEntity.ok(messageResponses);
    }

    @PostMapping
    public ResponseEntity<?> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest sendMessageRequest,
            Principal principal) {
        try {
            // 채팅방 존재 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            // 사용자 확인 및 참여 여부 검증
            User sender = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            if (!room.getParticipantIds().contains(sender.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방에 참여하지 않은 사용자입니다."));
            }

            // 메시지 생성
            Message message = Message.builder()
                    .roomId(roomId)
                    .senderId(sender.getId())
                    .content(sendMessageRequest.getContent())
                    .type(sendMessageRequest.getType() != null ? sendMessageRequest.getType() : MessageType.TEXT)
                    .aiType(sendMessageRequest.getAiType())
                    .mentions(sendMessageRequest.getMentions())
                    .timestamp(LocalDateTime.now())
                    .isDeleted(false)
                    .reactions(new HashMap<>())
                    .build();

            Message savedMessage = messageRepository.save(message);
            MessageResponse messageResponse = mapToMessageResponse(savedMessage);

            return ResponseEntity.status(HttpStatus.CREATED).body(messageResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "메시지 전송 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/with-file")
    public ResponseEntity<?> sendMessageWithFile(
            @PathVariable String roomId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "mentions", required = false) List<String> mentions,
            Principal principal) {
        try {
            // 채팅방 존재 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            // 사용자 확인 및 참여 여부 검증
            User sender = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            if (!room.getParticipantIds().contains(sender.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방에 참여하지 않은 사용자입니다."));
            }

            // 파일 업로드
            String fileName = fileService.storeFile(file);

            // 파일 메타데이터 저장
            File fileEntity = File.builder()
                    .filename(fileName)
                    .originalname(file.getOriginalFilename())
                    .mimetype(file.getContentType())
                    .size(file.getSize())
                    .uploadedBy(sender.getId())
                    .uploadedAt(LocalDateTime.now())
                    .build();

            File savedFile = fileRepository.save(fileEntity);

            // 메시지 생성
            Message message = Message.builder()
                    .roomId(roomId)
                    .senderId(sender.getId())
                    .content(content)
                    .type(MessageType.FILE)
                    .fileId(savedFile.getId())
                    .mentions(mentions)
                    .timestamp(LocalDateTime.now())
                    .isDeleted(false)
                    .reactions(new HashMap<>())
                    .build();

            Message savedMessage = messageRepository.save(message);
            MessageResponse messageResponse = mapToMessageResponse(savedMessage);

            return ResponseEntity.status(HttpStatus.CREATED).body(messageResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "파일 메시지 전송 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<?> updateMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @Valid @RequestBody SendMessageRequest updateRequest,
            Principal principal) {
        try {
            // 메시지 존재 확인
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found with id: " + messageId));

            // 사용자 확인
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 메시지 작성자 확인
            if (!message.getSenderId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "자신이 작성한 메시지만 수정할 수 있습니다."));
            }

            // 삭제된 메시지 확인
            if (message.isDeleted()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse(false, "삭제된 메시지는 수정할 수 없습니다."));
            }

            // 메시지 업데이트
            message.setContent(updateRequest.getContent());
            message.setMentions(updateRequest.getMentions());

            Message updatedMessage = messageRepository.save(message);
            MessageResponse messageResponse = mapToMessageResponse(updatedMessage);

            return ResponseEntity.ok(messageResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "메시지 수정 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            Principal principal) {
        try {
            // 메시지 존재 확인
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found with id: " + messageId));

            // 사용자 확인
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 메시지 작성자 확인
            if (!message.getSenderId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "자신이 작성한 메시지만 삭제할 수 있습니다."));
            }

            // 소프트 삭제
            message.setDeleted(true);
            message.setContent("[삭제된 메시지]");
            messageRepository.save(message);

            return ResponseEntity.ok(new AuthResponse(true, "메시지가 삭제되었습니다."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "메시지 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<?> addReaction(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @Valid @RequestBody MessageReactionRequest reactionRequest,
            Principal principal) {
        try {
            // 메시지 존재 확인
            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found with id: " + messageId));

            // 사용자 확인
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 채팅방 참여 여부 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방에 참여하지 않은 사용자입니다."));
            }

            // 반응 추가/제거
            Map<String, Set<String>> reactions = message.getReactions();
            if (reactions == null) {
                reactions = new HashMap<>();
            }

            String emoji = reactionRequest.getEmoji();
            Set<String> userIds = reactions.getOrDefault(emoji, new HashSet<>());

            if (userIds.contains(user.getId())) {
                // 이미 반응한 경우 제거
                userIds.remove(user.getId());
                if (userIds.isEmpty()) {
                    reactions.remove(emoji);
                } else {
                    reactions.put(emoji, userIds);
                }
            } else {
                // 새로운 반응 추가
                userIds.add(user.getId());
                reactions.put(emoji, userIds);
            }

            message.setReactions(reactions);
            messageRepository.save(message);

            return ResponseEntity.ok(new MessageReactionResponse(true, "반응이 업데이트되었습니다.", reactions));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "반응 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @PostMapping("/mark-as-read")
    public ResponseEntity<?> markMessagesAsRead(
            @PathVariable String roomId,
            @Valid @RequestBody MarkAsReadRequest markAsReadRequest,
            Principal principal) {
        try {
            // 사용자 확인
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 채팅방 참여 여부 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방에 참여하지 않은 사용자입니다."));
            }

            // 읽음 처리 로직 (실제 구현에서는 별도 ReadStatus 컬렉션 사용 권장)
            // 여기서는 간단히 응답만 반환
            String lastReadMessageId = markAsReadRequest.getLastReadMessageId();

            return ResponseEntity.ok(new MessagesReadResponse(true, "메시지 읽음 처리가 완료되었습니다.", lastReadMessageId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "읽음 처리 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchMessages(
            @PathVariable String roomId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        try {
            // 사용자 확인
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 채팅방 참여 여부 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방에 참여하지 않은 사용자입니다."));
            }

            // 메시지 검색
            Page<Message> searchResults = messageRepository.findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                    roomId, query, PageRequest.of(page, size));

            Page<MessageResponse> messageResponses = searchResults.map(this::mapToMessageResponse);

            return ResponseEntity.ok(messageResponses);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "메시지 검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    private MessageResponse mapToMessageResponse(Message message) {
        UserSummaryResponse senderSummary = null;
        if (message.getSenderId() != null) {
            User sender = userRepository.findById(message.getSenderId()).orElse(null);
            if (sender != null) {
                senderSummary = new UserSummaryResponse(
                        sender.getId(),
                        sender.getName(),
                        sender.getEmail(),
                        sender.getProfileImage()
                );
            }
        }

        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .sender(senderSummary)
                .type(message.getType())
                .timestamp(message.getTimestamp())
                .readCount(message.getReadCount()) // 읽음 상태 정보 추가
                .build();
    }
}
