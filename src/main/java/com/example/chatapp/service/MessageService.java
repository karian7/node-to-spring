package com.example.chatapp.service;

import com.example.chatapp.dto.ApiErrorCode;
import com.example.chatapp.dto.FileResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.exception.BusinessException;
import com.example.chatapp.exception.ResourceNotFoundException;
import com.example.chatapp.model.File;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 메시지 시스템 완성 서비스
 * Node.js backend의 메시지 기능과 동일한 수준 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final FileRepository fileRepository;
    private final RealTimeNotificationService notificationService;
    private final AiService aiService;

    /**
     * 메시지 생성 (파일 첨부, AI 응답 지원)
     */
    @Transactional
    public MessageResponse createMessage(String roomId, String content, String fileId, String senderId) {
        // 1. 권한 검증 - 룸 참여자인지 확인
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> ResourceNotFoundException.room(roomId));

        if (!room.getParticipantIds().contains(senderId)) {
            throw BusinessException.notRoomMember();
        }

        // 2. 발신자 정보 조회
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> ResourceNotFoundException.user(senderId));

        // 3. 메시지 생성
        Message.MessageBuilder messageBuilder = Message.builder()
                .roomId(roomId)
                .content(content)
                .senderId(senderId)
                .timestamp(LocalDateTime.now());

        // 4. 파일 첨부 처리
        if (fileId != null) {
            File attachedFile = fileRepository.findById(fileId)
                    .orElseThrow(() -> ResourceNotFoundException.file(fileId));

            messageBuilder.fileId(fileId)
                    .attachments(List.of(fileId));
        }

        // 5. AI 응답 여부 확인 및 처리
        if (shouldTriggerAiResponse(content, roomId)) {
            messageBuilder.aiPrompt(content);
            // AI 응답은 비동기로 처리
        }

        Message savedMessage = messageRepository.save(messageBuilder.build());

        // 6. 실시간 알림
        notificationService.notifyNewMessage(roomId, senderId, content);

        // 7. AI 응답 비동기 처리
        if (shouldTriggerAiResponse(content, roomId)) {
            processAiResponseAsync(savedMessage, room);
        }

        return mapToMessageResponse(savedMessage);
    }

    /**
     * 페이지네이션이 적용된 메시지 조회
     */
    public MessagesWithPaginationResponse getMessagesWithPagination(String roomId, String before, Integer limit) {
        // 기본값 설정
        int pageSize = limit != null ? Math.min(limit, 50) : 30;

        // 권한 검증은 컨트롤러에서 수행된 것으로 가정

        Page<Message> messagePage;
        if (before != null) {
            // before 타임스탬프 이전의 메시지들 조회
            LocalDateTime beforeTime = LocalDateTime.parse(before);
            messagePage = messageRepository.findByRoomIdAndTimestampBeforeOrderByTimestampDesc(
                    roomId, beforeTime, PageRequest.of(0, pageSize));
        } else {
            // 최신 메시지들 조회
            messagePage = messageRepository.findByRoomIdOrderByTimestampDesc(
                    roomId, PageRequest.of(0, pageSize));
        }

        List<MessageResponse> messages = messagePage.getContent().stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());

        // 시간 순으로 정렬 (최신이 뒤에)
        messages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

        return MessagesWithPaginationResponse.builder()
                .messages(messages)
                .hasMore(messagePage.hasNext())
                .oldestTimestamp(messages.isEmpty() ? null : messages.get(0).getTimestamp())
                .count(messages.size())
                .build();
    }

    /**
     * 메시지 읽음 상태 업데이트
     */
    @Transactional
    public void markMessageAsRead(String messageId, String userId, String deviceId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.message(messageId));

        // 이미 읽은 사용자인지 확인
        boolean alreadyRead = message.getReaders().stream()
                .anyMatch(reader -> reader.getUserId().equals(userId));

        if (!alreadyRead) {
            Message.MessageReader reader = Message.MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .deviceId(deviceId)
                    .build();

            message.getReaders().add(reader);
            messageRepository.save(message);

            // 실시간으로 읽음 상태 브로드캐스트
            notificationService.sendNotificationToRoom(
                    message.getRoomId(),
                    "message-read",
                    Map.of("messageId", messageId, "userId", userId),
                    null
            );
        }
    }

    /**
     * 룸의 모든 메시지 읽음 처리
     */
    @Transactional
    public void markAllMessagesAsRead(String roomId, String userId, String deviceId) {
        // 해당 사용자가 읽지 않은 메시지들 조회
        List<Message> unreadMessages = messageRepository.findUnreadMessagesForUser(roomId, userId);

        for (Message message : unreadMessages) {
            Message.MessageReader reader = Message.MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .deviceId(deviceId)
                    .build();

            message.getReaders().add(reader);
        }

        if (!unreadMessages.isEmpty()) {
            messageRepository.saveAll(unreadMessages);

            // 일괄 읽음 처리 알림
            notificationService.sendNotificationToRoom(
                    roomId,
                    "messages-read-all",
                    Map.of("userId", userId, "count", unreadMessages.size()),
                    null
            );
        }
    }

    /**
     * 메시지 수정
     */
    @Transactional
    public MessageResponse editMessage(String messageId, String newContent, String editorId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.message(messageId));

        // 권한 검증 - 메시지 작성자만 수정 가능
        if (!message.getSenderId().equals(editorId)) {
            throw new BusinessException(ApiErrorCode.FORBIDDEN, "메시지 수정 권한이 없습니다.");
        }

        message.setContent(newContent);
        message.setEditedAt(LocalDateTime.now());

        Message savedMessage = messageRepository.save(message);

        // 실시간 메시지 수정 알림
        notificationService.sendNotificationToRoom(
                message.getRoomId(),
                "message-edited",
                mapToMessageResponse(savedMessage),
                null
        );

        return mapToMessageResponse(savedMessage);
    }

    /**
     * 메시지 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteMessage(String messageId, String deleterId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.message(messageId));

        // 권한 검증 - 메시지 작성자만 삭제 가능
        if (!message.getSenderId().equals(deleterId)) {
            throw new BusinessException(ApiErrorCode.FORBIDDEN, "메시지 삭제 권한이 없습니다.");
        }

        message.setDeleted(true);
        message.setContent("[삭제된 메시지]");
        messageRepository.save(message);

        // 실시간 메시지 삭제 알림
        notificationService.sendNotificationToRoom(
                message.getRoomId(),
                "message-deleted",
                Map.of("messageId", messageId),
                null
        );
    }

    /**
     * 메시지 핀 설정/해제
     */
    @Transactional
    public MessageResponse toggleMessagePin(String messageId, String userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ResourceNotFoundException.message(messageId));

        // 권한 검증 - 룸 참여자만 핀 설정 가능
        Room room = roomRepository.findById(message.getRoomId())
                .orElseThrow(() -> ResourceNotFoundException.room(message.getRoomId()));

        if (!room.getParticipantIds().contains(userId)) {
            throw BusinessException.notRoomMember();
        }

        if (message.isPinned()) {
            // 핀 해제
            message.setPinned(false);
            message.setPinnedAt(null);
            message.setPinnedBy(null);
        } else {
            // 핀 설정
            message.setPinned(true);
            message.setPinnedAt(LocalDateTime.now());
            message.setPinnedBy(userId);
        }

        Message savedMessage = messageRepository.save(message);

        // 실시간 핀 상태 변경 알림
        notificationService.sendNotificationToRoom(
                message.getRoomId(),
                "message-pin-changed",
                mapToMessageResponse(savedMessage),
                null
        );

        return mapToMessageResponse(savedMessage);
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    public long getUnreadMessageCount(String roomId, String userId) {
        return messageRepository.countUnreadMessagesForUser(roomId, userId);
    }

    /**
     * AI 응답 트리거 조건 확인
     */
    private boolean shouldTriggerAiResponse(String content, String roomId) {
        // AI 멘션이나 특정 키워드가 포함된 경우
        return content != null && (
                content.contains("@ai") ||
                content.contains("@AI") ||
                content.startsWith("!ask") ||
                content.startsWith("!help")
        );
    }

    /**
     * AI 응답 비동기 처리
     */
    private void processAiResponseAsync(Message originalMessage, Room room) {
        // 비동기로 AI 응답 생성 및 전송
        CompletableFuture.runAsync(() -> {
            try {
                String aiResponse = aiService.generateResponse(
                        originalMessage.getContent(),
                        originalMessage.getRoomId(),
                        originalMessage.getSenderId()
                );

                if (aiResponse != null && !aiResponse.trim().isEmpty()) {
                    // AI 응답 메시지 생성
                    Message aiMessage = Message.builder()
                            .roomId(originalMessage.getRoomId())
                            .content(aiResponse)
                            .senderId("ai-system") // AI 시스템 사용자 ID
                            .type(originalMessage.getType())
                            .aiType(originalMessage.getAiType())
                            .replyToMessageId(originalMessage.getId())
                            .timestamp(LocalDateTime.now())
                            .build();

                    Message savedAiMessage = messageRepository.save(aiMessage);

                    // 실시간으로 AI 응답 전송
                    notificationService.sendNotificationToRoom(
                            originalMessage.getRoomId(),
                            "ai-response",
                            mapToMessageResponse(savedAiMessage),
                            null
                    );
                }

            } catch (Exception e) {
                log.error("AI 응답 생성 중 에러 발생", e);
            }
        });
    }

    /**
     * Message를 MessageResponse로 변환
     */
    private MessageResponse mapToMessageResponse(Message message) {
        User sender = userRepository.findById(message.getSenderId()).orElse(null);
        File attachedFile = null;

        if (message.getFileId() != null) {
            attachedFile = fileRepository.findById(message.getFileId()).orElse(null);
        }

        return MessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .content(message.getContent())
                .sender(sender != null ? UserResponse.from(sender) : null)
                .type(message.getType())
                .fileId(message.getFileId())
                .file(attachedFile != null ? FileResponse.from(attachedFile) : null)
                .aiType(message.getAiType())
                .mentions(message.getMentions())
                .timestamp(message.getTimestamp())
                .isDeleted(message.isDeleted())
                .reactions(message.getReactions())
                .readers(message.getReaders())
                .editedAt(message.getEditedAt())
                .replyToMessageId(message.getReplyToMessageId())
                .isPinned(message.isPinned())
                .pinnedAt(message.getPinnedAt())
                .pinnedBy(message.getPinnedBy())
                .attachments(message.getAttachments())
                .build();
    }

    // 응답 DTO 클래스들
    @lombok.Builder
    @lombok.Data
    public static class MessagesWithPaginationResponse {
        private List<MessageResponse> messages;
        private boolean hasMore;
        private LocalDateTime oldestTimestamp;
        private int count;
    }
}
