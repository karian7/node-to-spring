package com.example.chatapp.service;

import com.example.chatapp.model.Message;
import com.example.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    private static final int BATCH_SIZE = 30; // Node.js와 동일한 배치 크기

    /**
     * 배치 로딩으로 메시지 조회 (Node.js의 loadMessages 기능 구현)
     */
    public MessageLoadResult loadMessages(String roomId, LocalDateTime before, String userId, int limit) {
        try {
            log.debug("Loading messages for room: {}, before: {}, userId: {}, limit: {}",
                     roomId, before, userId, limit);

            // 페이지 설정
            Pageable pageable = PageRequest.of(0, limit + 1, Sort.by(Sort.Direction.DESC, "timestamp"));

            Page<Message> messagesPage;
            if (before != null) {
                messagesPage = messageRepository.findByRoomIdAndTimestampBeforeOrderByTimestampDesc(
                    roomId, before, pageable);
            } else {
                messagesPage = messageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
            }

            List<Message> messages = messagesPage.getContent();

            // hasMore 계산
            boolean hasMore = messages.size() > limit;
            if (hasMore) {
                messages = messages.subList(0, limit);
            }

            // 시간 순으로 정렬 (오래된 것부터)
            messages.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

            // 비동기로 읽음 상태 업데이트
            if (!messages.isEmpty() && userId != null) {
                final List<Message> messagesAsRead = messages;
                CompletableFuture.runAsync(() -> markMessagesAsRead(messagesAsRead, userId));
            }

            LocalDateTime oldestTimestamp = messages.isEmpty() ? null : messages.get(0).getTimestamp();

            return MessageLoadResult.builder()
                    .messages(messages)
                    .hasMore(hasMore)
                    .oldestTimestamp(oldestTimestamp)
                    .build();

        } catch (Exception e) {
            log.error("Error loading messages for room: {}", roomId, e);
            throw new RuntimeException("메시지 로딩 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 메시지들을 읽음 상태로 비동기 업데이트
     */
    private void markMessagesAsRead(List<Message> messages, String userId) {
        try {
            List<String> messageIds = messages.stream()
                    .filter(msg -> !msg.isReadBy(userId))
                    .map(Message::getId)
                    .toList();

            if (!messageIds.isEmpty()) {
                messageRepository.markMessagesAsReadByUser(messageIds, userId, LocalDateTime.now());
                log.debug("Marked {} messages as read for user: {}", messageIds.size(), userId);
            }
        } catch (Exception e) {
            log.error("Error marking messages as read for user: {}", userId, e);
        }
    }

    /**
     * 단일 메시지를 읽음 상태로 표시
     */
    public void markMessageAsRead(String messageId, String userId) {
        try {
            Message message = messageRepository.findById(messageId).orElse(null);
            if (message != null && !message.isReadBy(userId)) {
                message.markAsReadBy(userId);
                messageRepository.save(message);
                log.debug("Message {} marked as read by user: {}", messageId, userId);
            }
        } catch (Exception e) {
            log.error("Error marking message {} as read for user: {}", messageId, userId, e);
        }
    }

    /**
     * 방의 모든 메시지를 읽음 상태로 표시
     */
    public void markAllMessagesInRoomAsRead(String roomId, String userId) {
        try {
            messageRepository.markAllMessagesInRoomAsReadByUser(roomId, userId, LocalDateTime.now());
            log.debug("All messages in room {} marked as read by user: {}", roomId, userId);
        } catch (Exception e) {
            log.error("Error marking all messages in room {} as read for user: {}", roomId, userId, e);
        }
    }

    /**
     * 사용자의 읽지 않은 메시지 수 조회
     */
    public long getUnreadMessageCount(String roomId, String userId) {
        try {
            return messageRepository.countUnreadMessagesInRoom(roomId, userId);
        } catch (Exception e) {
            log.error("Error getting unread message count for room: {}, user: {}", roomId, userId, e);
            return 0;
        }
    }

    /**
     * 메시지 저장
     */
    public Message saveMessage(Message message) {
        return messageRepository.save(message);
    }

    /**
     * 메시지 로딩 결과를 담는 클래스
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MessageLoadResult {
        private List<Message> messages;
        private boolean hasMore;
        private LocalDateTime oldestTimestamp;
    }
}
