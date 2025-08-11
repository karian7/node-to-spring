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
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final MessageService messageService;

    // Node.js 버전과 동일한 상수들
    private static final int BATCH_SIZE = 30;
    private static final int MAX_LIMIT = 50;

    /**
     * Node.js Socket.IO와 동일한 방식으로 메시지 조회
     * GET /api/rooms/{roomId}/messages
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMessages(
            @PathVariable String roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime before,
            @RequestParam(defaultValue = "30") int limit,
            Principal principal) {

        try {
            // 사용자 인증
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 방 존재 및 권한 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("채팅방 접근 권한이 없습니다."));
            }

            // Node.js와 동일한 메시지 로딩 로직
            int actualLimit = Math.min(limit, MAX_LIMIT);
            LocalDateTime beforeTime = before != null ? before : LocalDateTime.now();

            // 메시지 조회 (limit + 1로 hasMore 판단)
            Pageable pageable = PageRequest.of(0, actualLimit + 1, Sort.by("timestamp").descending());
            Page<Message> messagePage = messageRepository.findByRoomIdAndTimestampBefore(
                    roomId, beforeTime, pageable);

            List<Message> allMessages = messagePage.getContent();
            boolean hasMore = allMessages.size() > actualLimit;
            List<Message> messages = hasMore ? allMessages.subList(0, actualLimit) : allMessages;

            // 메시지를 시간순으로 정렬 (Node.js와 동일)
            List<Message> sortedMessages = messages.stream()
                    .sorted(Comparator.comparing(Message::getTimestamp))
                    .toList();

            // 사용자 정보 조회
            Set<String> senderIds = sortedMessages.stream()
                    .map(Message::getSenderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            // 메시지 응답 생성 (Node.js와 동일한 구조)
            List<MessageResponse> messageResponses = sortedMessages.stream()
                    .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                    .toList();

            // 읽음 상태 업데이트 (비동기)
            if (!sortedMessages.isEmpty()) {
                updateReadStatusAsync(sortedMessages, user.getId());
            }

            // Node.js와 동일한 응답 구조
            LocalDateTime oldestTimestamp = !sortedMessages.isEmpty() ?
                sortedMessages.get(0).getTimestamp() : null;

            Map<String, Object> result = Map.of(
                "messages", messageResponses,
                "hasMore", hasMore,
                "oldestTimestamp", Objects.toString(oldestTimestamp, "")
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error loading messages for room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 로딩 중 오류가 발생했습니다."));
        }
    }

    /**
     * Node.js와 동일한 메시지 전송 로직
     * POST /api/rooms/{roomId}/messages
     */
    @PostMapping
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request,
            Principal principal) {
        try {
            // 사용자 인증
            User sender = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 방 존재 및 권한 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            if (!room.getParticipantIds().contains(sender.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("채팅방에 참여하지 않은 사용자입니다."));
            }

            // 메시지 생성 (Node.js와 동일한 로직)
            Message message = new Message();
            message.setRoomId(roomId);
            message.setSenderId(sender.getId());
            message.setContent(request.getContent());
            message.setType(request.getType() != null ? request.getType() : MessageType.TEXT);
            message.setMentions(request.getMentions());
            message.setTimestamp(LocalDateTime.now());
            message.setReactions(new HashMap<>());

            // 파일 메시지 처리
            if (request.getType() == MessageType.FILE && request.getFileId() != null) {
                com.example.chatapp.model.File file = fileRepository.findById(request.getFileId())
                        .orElse(null);
                if (file != null && file.getUploadedBy().equals(sender.getId())) {
                    message.setFileId(file.getId());
                    message.setMetadata(Message.FileMetadata.builder()
                            .fileType(file.getMimetype())
                            .fileSize(file.getSize())
                            .originalName(file.getOriginalname())
                            .build());
                }
            }

            Message savedMessage = messageRepository.save(message);
            MessageResponse messageResponse = mapToMessageResponse(savedMessage, sender);

            // TODO: WebSocket 브로드캐스트는 별도 서비스에서 처리
            log.info("Message sent to room: {} by user: {}", roomId, sender.getId());

            // 멘션 처리
            if (request.getMentions() != null && !request.getMentions().isEmpty()) {
                handleMentions(request.getMentions(), messageResponse, room);
            }

            // AI 멘션 처리
            if (message.getContent() != null) {
                handleAiMentions(message);
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("메시지가 전송되었습니다.", messageResponse));

        } catch (Exception e) {
            log.error("Error sending message to room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 전송 중 오류가 발생했습니다."));
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

            // TODO: WebSocket으로 읽음 상태 알림은 별도 서비스에서 처리
            log.info("Message marked as read: {} by user: {}", messageId, user.getId());

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

            // TODO: WebSocket으로 전체 읽음 상태 알림은 별도 서비스에서 처리
            log.info("All messages marked as read in room: {} by user: {}", roomId, user.getId());

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
     * Node.js와 동일한 메시지 반응 처리
     */
    @PostMapping("/{messageId}/reactions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> toggleReaction(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @Valid @RequestBody MessageReactionRequest reactionRequest,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            Message message = messageRepository.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));

            // 권한 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("채팅방에 참여하지 않은 사용자입니다."));
            }

            // 반응 토글 (Node.js와 동일한 로직)
            Map<String, Set<String>> reactions = message.getReactions();
            if (reactions == null) {
                reactions = new HashMap<>();
            }

            String emoji = reactionRequest.getReaction();
            Set<String> userIds = reactions.computeIfAbsent(emoji, k -> new HashSet<>());

            String action;
            if (userIds.contains(user.getId())) {
                userIds.remove(user.getId());
                action = "remove";
                if (userIds.isEmpty()) {
                    reactions.remove(emoji);
                }
            } else {
                userIds.add(user.getId());
                action = "add";
            }

            message.setReactions(reactions);
            messageRepository.save(message);

            // TODO: WebSocket 브로드캐스트는 별도 서비스에서 처리
            Map<String, Object> reactionUpdate = Map.of(
                "messageId", messageId,
                "reactions", reactions,
                "action", action,
                "userId", user.getId(),
                "emoji", emoji
            );

            log.info("Reaction updated for message: {} by user: {}", messageId, user.getId());

            return ResponseEntity.ok(ApiResponse.success("반응이 업데이트되었습니다.", reactionUpdate));

        } catch (Exception e) {
            log.error("Error handling reaction for message: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("반응 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * 메시지 읽음 상태 업데이트 (Node.js와 동일)
     */
    @PostMapping("/mark-read")
    public ResponseEntity<ApiResponse<Void>> markMessagesAsRead(
            @PathVariable String roomId,
            @Valid @RequestBody MarkAsReadRequest request,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 권한 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("채팅방에 참여하지 않은 사용자입니다."));
            }

            // 읽음 상태 업데이트
            if (request.getMessageIds() != null && !request.getMessageIds().isEmpty()) {
                List<Message> messages = messageRepository.findAllById(request.getMessageIds());

                Message.MessageReader readerInfo = Message.MessageReader.builder()
                        .userId(user.getId())
                        .readAt(LocalDateTime.now())
                        .build();

                messages.forEach(message -> {
                    if (message.getReaders() == null) {
                        message.setReaders(new ArrayList<>());
                    }
                    boolean alreadyRead = message.getReaders().stream()
                            .anyMatch(r -> r.getUserId().equals(user.getId()));
                    if (!alreadyRead) {
                        message.getReaders().add(readerInfo);
                    }
                });

                messageRepository.saveAll(messages);

                // TODO: WebSocket 브로드캐스트는 별도 서비스에서 처리
                log.info("Messages marked as read by user: {} in room: {}", user.getId(), roomId);
            }

            return ResponseEntity.ok(ApiResponse.success("읽음 상태가 업데이트되었습니다.", null));

        } catch (Exception e) {
            log.error("Error marking messages as read in room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("읽음 상태 업데이트 중 오류가 발생했습니다."));
        }
    }

    /**
     * 메시지 검색 (Node.js와 동일한 기능)
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchMessages(
            @PathVariable String roomId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            // 권한 확인
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new RuntimeException("Room not found"));

            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error("채팅방에 참여하지 않은 사용자입니다."));
            }

            // 메시지 검색
            Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("timestamp").descending());
            Page<Message> searchResults = messageRepository.findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(
                    roomId, query, pageable);

            // 사용자 정보 조회
            Set<String> senderIds = searchResults.getContent().stream()
                    .map(Message::getSenderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));

            // 메시지 응답 생성
            List<MessageResponse> messageResponses = searchResults.getContent().stream()
                    .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                    .toList();

            Map<String, Object> result = Map.of(
                "messages", messageResponses,
                "currentPage", page,
                "totalPages", searchResults.getTotalPages(),
                "totalElements", searchResults.getTotalElements(),
                "hasNext", searchResults.hasNext()
            );

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (Exception e) {
            log.error("Error searching messages in room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 검색 중 오류가 발생했습니다."));
        }
    }

    /**
     * 파일과 함께 메시지 전송
     */
    @PostMapping("/with-file")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessageWithFile(
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
                        .body(ApiResponse.error("채팅방에 참여하지 않은 사용자입니다."));
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
            MessageResponse messageResponse = mapToMessageResponse(savedMessage, sender);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("파일 메시지가 전송되었습니다.", messageResponse));

        } catch (Exception e) {
            log.error("Error sending file message to room: {}", roomId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("파일 메시지 전송 중 오류가 발생했습니다."));
        }
    }

    /**
     * 메시지 수정
     */
    @PutMapping("/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponse>> updateMessage(
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
                        .body(ApiResponse.error("자신이 작성한 메시지만 수정할 수 있습니다."));
            }

            // 삭제된 메시지 확인
            if (message.isDeleted()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("삭제된 메시지는 수정할 수 없습니다."));
            }

            // 메시지 업데이트
            message.setContent(updateRequest.getContent());
            message.setMentions(updateRequest.getMentions());

            Message updatedMessage = messageRepository.save(message);
            MessageResponse messageResponse = mapToMessageResponse(updatedMessage, user);

            return ResponseEntity.ok(ApiResponse.success("메시지가 수정되었습니다.", messageResponse));

        } catch (Exception e) {
            log.error("Error updating message: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 수정 중 오류가 발생했습니다."));
        }
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
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
                        .body(ApiResponse.error("자신이 작성한 메시지만 삭제할 수 있습니다."));
            }

            // 소프트 삭제
            message.setDeleted(true);
            message.setContent("[삭제된 메시지]");
            messageRepository.save(message);

            return ResponseEntity.ok(ApiResponse.success("메시지가 삭제되었습니다.", null));

        } catch (Exception e) {
            log.error("Error deleting message: {}", messageId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("메시지 삭제 중 오류가 발생했습니다."));
        }
    }

    /**
     * Node.js와 동일한 메시지 응답 매핑
     */
    private MessageResponse mapToMessageResponse(Message message, User sender) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.getTimestamp())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ? message.getReactions() : new HashMap<>())
                .readers(message.getReaders() != null ? message.getReaders() : new ArrayList<>())
                .mentions(message.getMentions());

        if (sender != null) {
            builder.sender(UserResponse.builder()
                    .id(sender.getId())
                    .name(sender.getName())
                    .email(sender.getEmail())
                    .profileImage(sender.getProfileImage())
                    .build());
        }

        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId()).ifPresent(file ->
                builder.file(FileResponse.builder()
                        .id(file.getId())
                        .filename(file.getFilename())
                        .originalname(file.getOriginalname())
                        .mimetype(file.getMimetype())
                        .size(file.getSize())
                        .build())
            );
        }

        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }

    /**
     * 읽음 상태 비동기 업데이트
     */
    private void updateReadStatusAsync(List<Message> messages, String userId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<String> messageIds = messages.stream()
                        .map(Message::getId)
                        .toList();

                List<Message> messagesToUpdate = messageRepository.findAllById(messageIds);

                Message.MessageReader readerInfo = Message.MessageReader.builder()
                        .userId(userId)
                        .readAt(LocalDateTime.now())
                        .build();

                messagesToUpdate.forEach(message -> {
                    if (message.getReaders() == null) {
                        message.setReaders(new ArrayList<>());
                    }
                    boolean alreadyRead = message.getReaders().stream()
                            .anyMatch(r -> r.getUserId().equals(userId));
                    if (!alreadyRead) {
                        message.getReaders().add(readerInfo);
                    }
                });

                messageRepository.saveAll(messagesToUpdate);

            } catch (Exception e) {
                log.error("Error updating read status asynchronously", e);
            }
        });
    }

    /**
     * 멘션 처리
     */
    private void handleMentions(List<String> mentions, MessageResponse messageResponse, Room room) {
        // TODO: 실제 멘션 알림 처리는 별도 서비스에서 구현
        log.info("Mentions detected: {} in room: {}", mentions, room.getId());
    }

    /**
     * AI 멘션 처리
     */
    private void handleAiMentions(Message message) {
        if (message.getContent() != null) {
            String content = message.getContent();
            List<AiType> aiMentions = new ArrayList<>();

            if (content.contains("@gpt")) aiMentions.add(AiType.GPT);
            if (content.contains("@claude")) aiMentions.add(AiType.CLAUDE);
            if (content.contains("@gemini")) aiMentions.add(AiType.GEMINI);

            // AI 응답은 별도 서비스에서 처리
            if (!aiMentions.isEmpty()) {
                log.info("AI mentions detected: {} in message: {}", aiMentions, message.getId());
                // TODO: AI 서비스 호출
            }
        }
    }
}
