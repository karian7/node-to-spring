package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.FetchMessagesRequest;
import com.example.chatapp.dto.FetchMessagesResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 메시지 조회 처리 핸들러
 * 이전 메시지 조회, 재시도 로직, 읽음 상태 업데이트 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageFetchHandler {

    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;

    private final Map<String, Boolean> messageQueues = new ConcurrentHashMap<>();
    private final Map<String, Integer> messageLoadRetries = new ConcurrentHashMap<>();

    // 상수 정의
    private static final int BATCH_SIZE = 30;
    private static final int LOAD_DELAY_MS = 300;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_BASE_DELAY_MS = 2000;
    private static final int MESSAGE_LOAD_TIMEOUT_MS = 10000;
    private static final int MAX_RETRY_DELAY_MS = 10000;
    
    @OnEvent(FETCH_PREVIOUS_MESSAGES)
    public void handleFetchMessages(SocketIOClient client, FetchMessagesRequest data) {
        try {
            String userId = getUserId(client);
            if (userId == null) {
                client.sendEvent(ERROR, Map.of(
                        "code", "UNAUTHORIZED",
                        "message", "인증이 필요합니다."
                ));
                return;
            }

            String queueKey = data.roomId() + ":" + userId;

            // 이미 로드 중인지 확인
            if (Boolean.TRUE.equals(messageQueues.get(queueKey))) {
                log.debug("Message load skipped - already loading for user {} in room {}",
                        userId, data.roomId());
                return;
            }

            // 권한 체크
            Room room = roomRepository.findById(data.roomId()).orElse(null);
            if (room == null || !room.getParticipantIds().contains(userId)) {
                client.sendEvent(ERROR, Map.of(
                        "code", "LOAD_ERROR",
                        "message", "채팅방 접근 권한이 없습니다."
                ));
                return;
            }

            messageQueues.put(queueKey, true);
            client.sendEvent(MESSAGE_LOAD_START);

            log.debug("Starting message load for user {} in room {}, limit: {}",
                    userId, data.roomId(), data.limit());

            // 재시도 로직으로 메시지 로드
            loadMessagesWithRetry(client, data.roomId(), data.limit(), userId, queueKey)
                    .thenAccept(result -> {
                        log.debug("Previous messages loaded - room: {}, count: {}, hasMore: {}, oldestTimestamp: {}",
                                data.roomId(), result.getMessages().size(),
                                result.isHasMore(), result.getOldestTimestamp());

                        client.sendEvent(PREVIOUS_MESSAGES_LOADED, result);

                        // 딜레이 후 큐 정리
                        CompletableFuture.delayedExecutor(LOAD_DELAY_MS, TimeUnit.MILLISECONDS)
                                .execute(() -> messageQueues.remove(queueKey));
                    })
                    .exceptionally(throwable -> {
                        log.error("Message load failed for user {} in room {}",
                                userId, data.roomId(), throwable);
                        client.sendEvent(ERROR, Map.of(
                                "code", "LOAD_ERROR",
                                "message", throwable.getMessage() != null ?
                                        throwable.getMessage() : "이전 메시지를 불러오는 중 오류가 발생했습니다."
                        ));
                        messageQueues.remove(queueKey);
                        return null;
                    });

        } catch (Exception e) {
            log.error("Error handling fetchPreviousMessages", e);
            client.sendEvent(ERROR, Map.of(
                    "code", "LOAD_ERROR",
                    "message", "메시지 로드 중 오류가 발생했습니다."
            ));
        }
    }

    /**
     * 재시도 로직을 포함한 메시지 로드
     */
    private CompletableFuture<FetchMessagesResponse> loadMessagesWithRetry(
            SocketIOClient client, String roomId, int limit,
            String userId, String retryKey) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Integer currentRetries = messageLoadRetries.get(retryKey);
                if (currentRetries != null && currentRetries >= MAX_RETRIES) {
                    throw new RuntimeException("최대 재시도 횟수를 초과했습니다.");
                }

                Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").ascending());
                LocalDateTime beforeTime = LocalDateTime.now();

                Page<Message> messagePage = messageRepository.findByRoomIdAndIsDeletedAndTimestampBefore(
                        roomId, false, beforeTime, pageable);

                List<Message> messages = messagePage.getContent();

                // 사용자 정보 조회
                Set<String> senderIds = messages.stream()
                        .map(Message::getSenderId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

                // 메시지 응답 생성
                List<MessageResponse> messageResponses = messages.stream()
                        .map(message -> messageResponseMapper.mapToMessageResponse(
                                message, userMap.get(message.getSenderId())))
                        .collect(Collectors.toList());

                // 읽음 상태 업데이트 (비동기)
                if (!messages.isEmpty()) {
                    updateReadStatusAsync(messages, userId);
                }

                log.debug("Fetch previous messages - roomId: {}, limit: {}, count: {}, hasNext: {}",
                        roomId, limit, messageResponses.size(), messagePage.hasNext());

                // oldestTimestamp를 ISO_INSTANT 형식으로 변환
                String oldestTimestampStr = convertToIsoTimestamp(messages);

                messageLoadRetries.remove(retryKey);
                return new FetchMessagesResponse(
                        messageResponses,
                        messagePage.hasNext(),
                        oldestTimestampStr
                );

            } catch (Exception e) {
                return handleLoadRetry(client, roomId, limit, userId, retryKey, e);
            }
        })
        .orTimeout(MESSAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .exceptionally(throwable -> {
            Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                    ? throwable.getCause()
                    : throwable;

            if (cause instanceof java.util.concurrent.TimeoutException timeout) {
                log.debug("Message load timeout - roomId: {}, limit: {}", roomId, limit);
                throw new RuntimeException("Message loading timed out", timeout);
            }

            throw new RuntimeException(cause);
        });
    }

    /**
     * 재시도 처리 로직
     */
    private FetchMessagesResponse handleLoadRetry(
            SocketIOClient client, String roomId, int limit,
            String userId, String retryKey, Exception e) {

        Integer currentRetries = messageLoadRetries.getOrDefault(retryKey, 0);

        if (currentRetries < MAX_RETRIES) {
            messageLoadRetries.put(retryKey, currentRetries + 1);
            int delay = Math.min(
                    RETRY_BASE_DELAY_MS * (int) Math.pow(2, currentRetries),
                    MAX_RETRY_DELAY_MS);

            log.debug("Retrying message load for room {} (attempt {}/{})",
                    roomId, currentRetries + 1, MAX_RETRIES);

            try {
                Thread.sleep(delay);
                return loadMessagesWithRetry(client, roomId, limit, userId, retryKey).get();
            } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }

        messageLoadRetries.remove(retryKey);
        throw new RuntimeException(e.getMessage(), e);
    }

    /**
     * 초기 메시지 로드 (방 입장 시)
     */
    public FetchMessagesResponse loadInitialMessages(String roomId, String userId) {
        CompletableFuture<FetchMessagesResponse> future =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        Pageable pageable = PageRequest.of(0, BATCH_SIZE,
                                Sort.by("timestamp").descending());

                        Page<Message> messagePage = messageRepository
                                .findByRoomIdAndIsDeletedAndTimestampBefore(
                                        roomId, false, LocalDateTime.now(), pageable);

                        List<Message> messages = messagePage.getContent();

                        boolean hasMore = messages.size() > BATCH_SIZE;
                        List<Message> resultMessages = messages.size() > BATCH_SIZE
                                ? messages.subList(0, BATCH_SIZE)
                                : messages;

                        List<Message> sortedMessages = resultMessages.stream()
                                .sorted(Comparator.comparing(Message::getTimestamp))
                                .toList();

                        if (userId != null && !sortedMessages.isEmpty()) {
                            updateReadStatusAsync(sortedMessages, userId);
                        }

                        Set<String> senderIds = sortedMessages.stream()
                                .map(Message::getSenderId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet());

                        Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                                .collect(Collectors.toMap(User::getId, user -> user));

                        List<MessageResponse> messageResponses = sortedMessages.stream()
                                .map(message -> messageResponseMapper.mapToMessageResponse(
                                        message, userMap.get(message.getSenderId())))
                                .collect(Collectors.toList());

                        String oldestTimestampStr = convertToIsoTimestamp(sortedMessages);

                        log.debug("Initial messages loaded - roomId: {}, userId: {}, count: {}, hasMore: {}",
                                roomId, userId, messageResponses.size(), hasMore);

                        return FetchMessagesResponse.builder()
                                .messages(messageResponses)
                                .hasMore(hasMore)
                                .oldestTimestamp(oldestTimestampStr)
                                .build();

                    } catch (Exception e) {
                        log.error("Error loading initial messages for room {}", roomId, e);
                        return FetchMessagesResponse.builder()
                                .messages(new ArrayList<>())
                                .hasMore(false)
                                .oldestTimestamp(null)
                                .build();
                    }
                });

        try {
            return future
                    .orTimeout(MESSAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .join();
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException timeout) {
                log.debug("Initial message load timeout - roomId: {}", roomId);
                throw new RuntimeException("Message loading timed out", timeout);
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * 읽음 상태 비동기 업데이트
     */
    private void updateReadStatusAsync(List<Message> messages, String userId) {
        CompletableFuture.runAsync(() -> {
            try {
                List<String> messageIds = messages.stream()
                        .map(Message::getId)
                        .collect(Collectors.toList());

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
                log.error("Read status update error", e);
            }
        });
    }

    /**
     * LocalDateTime을 ISO_INSTANT 형식으로 변환
     */
    private String convertToIsoTimestamp(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }

        Message firstMessage = messages.getFirst();
        if (firstMessage.getTimestamp() == null) {
            return null;
        }

        Instant instant = firstMessage.getTimestamp()
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return instant.toString();
    }

    private String getUserId(SocketIOClient client) {
        var user = (UserResponse) client.get("user");
        return user.getId();
    }
}
