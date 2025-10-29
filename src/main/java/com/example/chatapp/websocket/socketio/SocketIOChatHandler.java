package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.example.chatapp.dto.*;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.MessageType;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.util.JwtUtil;
import com.example.chatapp.websocket.socketio.handler.ChatMessageHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
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
 * Socket.IO Chat Handler
 * 이벤트 이름과 인증 흐름을 정의한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOChatHandler {

    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final ChatMessageHandler chatMessageHandler;
    private final JwtUtil jwtUtil;

    // Online users management
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>(); // userId -> socketId
    private final Map<String, SocketIOClient> socketClients = new ConcurrentHashMap<>(); // socketId -> client

    private final Map<String, Boolean> messageQueues = new ConcurrentHashMap<>();
    private final Map<String, Integer> messageLoadRetries = new ConcurrentHashMap<>();

    // User rooms management
    private final Map<String, String> userRooms = new ConcurrentHashMap<>(); // userId -> roomId
    private final Map<String, PendingDuplicateLogin> pendingDuplicateLogins = new ConcurrentHashMap<>();
    private final ScheduledExecutorService duplicateLoginScheduler = Executors.newSingleThreadScheduledExecutor();

    // 이벤트 처리 관련 상수
    private static final int BATCH_SIZE = 30;
    private static final int LOAD_DELAY = 300;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 2000;
    private static final int DUPLICATE_LOGIN_TIMEOUT = 10000;
    private static final int MESSAGE_LOAD_TIMEOUT = 10000;

    @PostConstruct
    public void initializeEventHandlers() {
        // Connection event 핸들러 등록
        socketIOServer.addDisconnectListener(onDisconnect());

        // Chat events 핸들러 등록
        socketIOServer.addEventListener(CHAT_MESSAGE, Map.class, chatMessageHandler.getListener());
        socketIOServer.addEventListener(JOIN_ROOM, String.class, onJoinRoom());
        socketIOServer.addEventListener(LEAVE_ROOM, String.class, onLeaveRoom());
        socketIOServer.addEventListener(FETCH_PREVIOUS_MESSAGES, FetchMessagesRequest.class, onFetchPreviousMessagesWithRetry());
        socketIOServer.addEventListener(MARK_MESSAGES_AS_READ, MarkAsReadRequest.class, onMarkMessagesAsRead());
        socketIOServer.addEventListener(MESSAGE_REACTION, MessageReactionRequest.class, onMessageReaction());
        socketIOServer.addEventListener(FORCE_LOGIN, Map.class, onForceLogin());
        socketIOServer.addEventListener(KEEP_EXISTING_SESSION, Map.class, onKeepExistingSession());

        log.info("Socket.IO event handlers initialized");
    }

    @PreDestroy
    public void shutdownScheduler() {
        duplicateLoginScheduler.shutdownNow();
    }
    
    public void onConnect(SocketIOClient client) {
        try {
            String userId = getUserId(client);
            String userName = getUserName(client);
            
            if (userId != null) {
                // Handle duplicate connections
                String existingSocketId = connectedUsers.get(userId);
                String newSocketId = client.getSessionId().toString();
                if (existingSocketId != null) {
                    SocketIOClient existingClient = socketClients.get(existingSocketId);
                    if (existingClient != null) {
                        // Send duplicate login notification
                        existingClient.sendEvent(DUPLICATE_LOGIN, Map.of(
                                "type", "new_login_attempt",
                                "deviceInfo", client.getHandshakeData().getHttpHeaders().get("User-Agent"),
                                "ipAddress", client.getRemoteAddress().toString(),
                                "timestamp", System.currentTimeMillis()
                        ));
                        
                        PendingDuplicateLogin previousPending = pendingDuplicateLogins.remove(userId);
                        if (previousPending != null) {
                            previousPending.cancelTimeout();
                        }
                        
                        PendingDuplicateLogin pending = new PendingDuplicateLogin(existingClient, existingSocketId, client, newSocketId);
                        ScheduledFuture<?> timeoutTask = duplicateLoginScheduler.schedule(() -> {
                            try {
                                existingClient.sendEvent(SESSION_ENDED, Map.of(
                                        "reason", "duplicate_login",
                                        "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
                                ));
                                existingClient.disconnect();
                            } finally {
                                pendingDuplicateLogins.remove(userId, pending);
                            }
                        }, DUPLICATE_LOGIN_TIMEOUT, TimeUnit.MILLISECONDS);
                        pending.setTimeoutTask(timeoutTask);
                        pendingDuplicateLogins.put(userId, pending);
                    }
                }
                
                // Store new connection
                connectedUsers.put(userId, newSocketId);
                socketClients.put(newSocketId, client);
                
                log.info("Socket.IO user connected: {} ({})", userName, userId);
                
                // Join user to their personal room for direct messages
                client.joinRoom("user:" + userId);
                
            }
        } catch (Exception e) {
            log.error("Error handling Socket.IO connection", e);
            client.sendEvent(ERROR, Map.of(
                    "message", "연결 처리 중 오류가 발생했습니다."
            ));
        }
    }

    private DisconnectListener onDisconnect() {
        return client -> {
            try {
                String socketId = client.getSessionId().toString();
                String userId = getUserId(client);
                String userName = getUserName(client);

                if (userId != null) {
                    String mappedSocketId = connectedUsers.get(userId);
                    if (socketId.equals(mappedSocketId)) {
                        connectedUsers.remove(userId);
                    }
                    socketClients.remove(socketId);

                    PendingDuplicateLogin pending = pendingDuplicateLogins.get(userId);
                    if (pending != null && pending.involves(socketId)) {
                        pending.cancelTimeout();
                        if (socketId.equals(pending.getNewSocketId())) {
                            String existingSocketId = pending.getExistingSocketId();
                            if (existingSocketId != null && socketClients.containsKey(existingSocketId)) {
                                connectedUsers.put(userId, existingSocketId);
                            }
                        }
                        pendingDuplicateLogins.remove(userId, pending);
                    }

                    log.info("Socket.IO user disconnected: {} ({})", userName, userId);
                }
            } catch (Exception e) {
                log.error("Error handling Socket.IO disconnection", e);
                client.sendEvent(ERROR, Map.of(
                    "message", "연결 종료 처리 중 오류가 발생했습니다."
                ));
            }
        };
    }


    public DataListener<String> onJoinRoom() {
        return (client, roomId, ackSender) -> {
            try {
                String userId = getUserId(client);
                String userName = getUserName(client);

                if (userId == null) {
                    client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "Unauthorized"));
                    return;
                }

                // 이미 해당 방에 참여 중인지 확인
                String currentRoom = userRooms.get(userId);
                if (roomId.equals(currentRoom)) {
                    log.debug("User {} already in room {}", userId, roomId);
                    client.joinRoom(roomId);
                    client.sendEvent(JOIN_ROOM_SUCCESS, Map.of("roomId", roomId));
                    return;
                }

                // 기존 방에서 나가기
                if (currentRoom != null) {
                    log.debug("User {} leaving current room {}", userId, currentRoom);
                    client.leaveRoom(currentRoom);
                    userRooms.remove(userId);

                    // 기존 방에 퇴장 알림
                    socketIOServer.getRoomOperations(currentRoom)
                        .sendEvent(USER_LEFT, Map.of(
                            "userId", userId,
                            "name", userName
                        ));
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "User not found"));
                    return;
                }

                // 채팅방 참가 with profileImage
                Room room = roomRepository.findById(roomId).orElse(null);
                if (room == null) {
                    client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                    return;
                }

                roomRepository.addParticipant(roomId, userId);
                room = roomRepository.findById(roomId).orElse(null);
                if (room == null) {
                    client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                    return;
                }

                // Join socket room
                client.joinRoom(roomId);
                userRooms.put(userId, roomId);

                Message joinMessage = Message.builder()
                    .roomId(roomId)
                    .content(userName + "님이 입장하였습니다.")
                    .type(MessageType.system)
                    .timestamp(LocalDateTime.now())
                    .mentions(new ArrayList<>())
                    .isDeleted(false)
                    .reactions(new HashMap<>())
                    .readers(new ArrayList<>())
                    .metadata(new HashMap<>())
                    .build();

                joinMessage = messageRepository.save(joinMessage);

                // 초기 메시지 로드
                FetchMessagesResponse messageLoadResult = loadInitialMessages(roomId, userId);

                // 참가자 정보 조회 (with profileImage)
                List<User> participantUsers = userRepository.findAllById(room.getParticipantIds());
                List<UserResponse> participants = participantUsers.stream()
                    .map(UserResponse::from)
                    .collect(Collectors.toList());

                // 활성 스트리밍 메시지 조회
                List<ActiveStreamResponse> activeStreams = chatMessageHandler.getStreamingSessions().stream()
                    .filter(session -> roomId.equals(session.getRoomId()))
                    .map(session -> {
                        // LocalDateTime을 ISO_INSTANT 형식으로 변환
                        String timestampStr = null;
                        if (session.getTimestamp() != null) {
                            Instant instant = session.getTimestamp()
                                .atZone(ZoneId.systemDefault())
                                .toInstant();
                            timestampStr = instant.toString();
                        }
                        
                        return ActiveStreamResponse.builder()
                            .id(session.getMessageId())
                            .type("ai")
                            .aiType(session.getAiType())
                            .content(session.getContent())
                            .timestamp(timestampStr)
                            .isStreaming(true)
                            .build();
                    })
                    .collect(Collectors.toList());

                // joinRoomSuccess 이벤트 발송
                JoinRoomSuccessResponse response = JoinRoomSuccessResponse.builder()
                    .roomId(roomId)
                    .participants(participants)
                    .messages(messageLoadResult.getMessages())
                    .hasMore(messageLoadResult.isHasMore())
                    .oldestTimestamp(messageLoadResult.getOldestTimestamp())
                    .activeStreams(activeStreams)
                    .build();

                client.sendEvent(JOIN_ROOM_SUCCESS, response);

                // 입장 메시지 브로드캐스트
                socketIOServer.getRoomOperations(roomId)
                    .sendEvent(MESSAGE, mapToMessageResponse(joinMessage, null));

                // 참가자 목록 업데이트 브로드캐스트
                socketIOServer.getRoomOperations(roomId)
                    .sendEvent(PARTICIPANTS_UPDATE, participants);

                log.info("User {} joined room {} successfully. Message count: {}, hasMore: {}",
                    userName, roomId, messageLoadResult.getMessages().size(), messageLoadResult.isHasMore());

            } catch (Exception e) {
                log.error("Error handling joinRoom", e);
                client.sendEvent(JOIN_ROOM_ERROR, Map.of(
                    "message", e.getMessage() != null ? e.getMessage() : "채팅방 입장에 실패했습���다."
                ));
            }
        };
    }

    public DataListener<String> onLeaveRoom() {
        return (client, roomId, ackSender) -> {
            try {
                String userId = getUserId(client);
                String userName = getUserName(client);

                if (userId == null) {
                    client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                    return;
                }

                String currentRoom = userRooms.get(userId);
                if (currentRoom == null || !currentRoom.equals(roomId)) {
                    log.debug("User {} is not in room {}", userId, roomId);
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                Room room = roomRepository.findById(roomId).orElse(null);

                if (user != null && room != null) {
                    room.getParticipantIds().remove(userId);
                    roomRepository.save(room);

                    client.leaveRoom(roomId);
                    userRooms.remove(userId);

                    log.info("User {} left room {}", userName, room.getName());

                    boolean removedStreams = chatMessageHandler.terminateStreamingSessions(roomId, userId);

                    String queueKey = roomId + ":" + userId;
                    Boolean queueRemoved = messageQueues.remove(queueKey);
                    Integer retryRemoved = messageLoadRetries.remove(queueKey);

                    log.debug("Leave room cleanup - roomId: {}, userId: {}, streamsCleared: {}, queueCleared: {}, retriesCleared: {}",
                        roomId, userId, removedStreams, queueRemoved != null, retryRemoved != null);

                    sendSystemMessage(roomId, userName + "님이 퇴장하였습니다.");
                    broadcastParticipantList(roomId);

                    // 빈 방 정리
                    if (room.getParticipantIds().isEmpty()) {
                        log.info("Room {} is now empty, deleting room", roomId);
                        roomRepository.deleteById(roomId);
                    }
                } else {
                    log.warn("Room {} not found or user {} has no access", roomId, userId);
                }

            } catch (Exception e) {
                log.error("Error handling leaveRoom", e);
                client.sendEvent(ERROR, Map.of("message", "채팅방 퇴장 중 오류가 발생했습니다."));
            }
        };
    }

    // 향상된 메시지 로드 기능
    private DataListener<FetchMessagesRequest> onFetchPreviousMessagesWithRetry() {
        return (client, data, ackSender) -> {
            try {
                String userId = getUserId(client);
                String queueKey = data.roomId() + ":" + userId;

                // 이미 로드 중인지 확인
                if (messageQueues.get(queueKey) != null && messageQueues.get(queueKey)) {
                    log.debug("Message load skipped - already loading for user {} in room {}", userId, data.roomId());
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

                log.debug("Starting message load for user {} in room {}, before: {}",
                    userId, data.roomId(), data.before());

                // 재시도 로직으로 메시지 로드
                loadMessagesWithRetry(client, data.roomId(), data.before(), queueKey)
                        .thenAccept(result -> {
                            log.debug("Previous messages loaded - room: {}, count: {}, hasMore: {}, oldestTimestamp: {}",
                                data.roomId(), result.getMessages().size(), result.isHasMore(), result.getOldestTimestamp());
                            
                            client.sendEvent(PREVIOUS_MESSAGES_LOADED, result);

                            // 딜레이 후 큐 정리
                            new Thread(() -> {
                                try {
                                    Thread.sleep(LOAD_DELAY);
                                    messageQueues.remove(queueKey);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }).start();
                        })
                        .exceptionally(throwable -> {
                            log.error("Message load failed for user {} in room {}", userId, data.roomId(), throwable);
                            client.sendEvent(ERROR, Map.of(
                                    "code", "LOAD_ERROR",
                                    "message", throwable.getMessage() != null ? throwable.getMessage() : "이전 메시지를 불러오는 중 오류가 발생했습니다."
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
        };
    }

    private DataListener<MarkAsReadRequest> onMarkMessagesAsRead() {
        return (client, data, ackSender) -> {
            try {
                String userId = getUserId(client);
                if (userId == null) {
                    client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                    return;
                }

                if (data == null || data.getMessageIds() == null || data.getMessageIds().isEmpty()) {
                    return;
                }
                
                var roomId = messageRepository.findById(data.getMessageIds().getFirst())
                        .map(Message::getRoomId).orElse(null);
                
                if (roomId == null || roomId.isBlank()) {
                    client.sendEvent(ERROR, Map.of("message", "Invalid room"));
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent(ERROR, Map.of("message", "User not found"));
                    return;
                }

                Room room = roomRepository.findById(roomId).orElse(null);
                if (room == null || !room.getParticipantIds().contains(userId)) {
                    client.sendEvent(ERROR, Map.of("message", "Room access denied"));
                    return;
                }

                Message.MessageReader readerInfo = Message.MessageReader.builder()
                        .userId(userId)
                        .readAt(LocalDateTime.now())
                        .build();

                long modifiedCount = messageRepository.addReaderToMessages(
                        data.getMessageIds(),
                        roomId,
                        userId,
                        readerInfo
                );

                if (modifiedCount == 0) {
                    log.debug("No messages marked as read - roomId: {}, userId: {}", roomId, userId);
                    return;
                }

                log.debug("Messages marked as read - roomId: {}, userId: {}, modified: {}",
                    roomId, userId, modifiedCount);

                MessagesReadResponse response = new MessagesReadResponse(userId, data.getMessageIds());

                // Broadcast to room
                socketIOServer.getRoomOperations(roomId)
                        .sendEvent(MESSAGES_READ, response);

            } catch (Exception e) {
                log.error("Error handling markMessagesAsRead", e);
                client.sendEvent(ERROR, Map.of(
                        "message", "읽음 상태 업데이트 중 오류가 발생했습니다."
                ));
            }
        };
    }

    private DataListener<MessageReactionRequest> onMessageReaction() {
        return (client, data, ackSender) -> {
            try {
                String userId = getUserId(client);
                if (userId == null || userId.isBlank()) {
                    client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                    return;
                }

                Message message = messageRepository.findById(data.getMessageId()).orElse(null);
                if (message == null) {
                    client.sendEvent(ERROR, Map.of("message", "메시지를 찾을 수 없습니다."));
                    return;
                }

                if (message.getReactions() == null) {
                    message.setReactions(new java.util.HashMap<>());
                }

                boolean changed = false;
                if ("add".equals(data.getType())) {
                    java.util.Set<String> userReactions = message.getReactions()
                        .computeIfAbsent(data.getReaction(), key -> new java.util.HashSet<>());
                    changed = userReactions.add(userId);
                } else if ("remove".equals(data.getType())) {
                    java.util.Set<String> userReactions = message.getReactions().get(data.getReaction());
                    if (userReactions != null && userReactions.remove(userId)) {
                        changed = true;
                        if (userReactions.isEmpty()) {
                            message.getReactions().remove(data.getReaction());
                        }
                    }
                } else {
                    client.sendEvent(ERROR, Map.of("message", "지원하지 않는 리액션 타입입니다."));
                    return;
                }

                if (!changed) {
                    return;
                }

                log.debug("Message reaction processed - type: {}, reaction: {}, messageId: {}, userId: {}",
                    data.getType(), data.getReaction(), message.getId(), userId);

                messageRepository.save(message);

                MessageReactionResponse response = new MessageReactionResponse(
                    message.getId(),
                    message.getReactions()
                );

                socketIOServer.getRoomOperations(message.getRoomId())
                    .sendEvent(MESSAGE_REACTION_UPDATE, response);

            } catch (Exception e) {
                log.error("Error handling messageReaction", e);
                client.sendEvent(ERROR, Map.of(
                    "message", "리액션 처리 중 오류가 발생했습니다."
                ));
            }
        };
    }

    // 강제 로그아웃 처리 (노드 버전과 동일) - Object 타입으로 수정
    @SuppressWarnings("rawtypes")
    private DataListener<Map> onForceLogin() {
        return (client, data, ackSender) -> {
            try {
                String userId = getUserId(client);
                if (userId == null) return;

                // data를 Map으로 캐스팅
                @SuppressWarnings("unchecked")
                Map<String, Object> requestData = (Map<String, Object>) data;
                String token = requestData != null ? (String) requestData.get("token") : null;
                if (token == null || token.isBlank()) {
                    client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                    return;
                }

                try {
                    if (!jwtUtil.validateToken(token)) {
                        throw new IllegalArgumentException("Token validation failed");
                    }

                    String tokenUserId = jwtUtil.extractSubject(token);
                    if (tokenUserId == null || !tokenUserId.equals(userId)) {
                        throw new IllegalArgumentException("Token user mismatch");
                    }
                } catch (Exception validationError) {
                    log.warn("Invalid token for force_login: {}", validationError.getMessage());
                    client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                    return;
                }

                PendingDuplicateLogin pending = pendingDuplicateLogins.remove(userId);
                if (pending != null) {
                    pending.cancelTimeout();
                }

                // 세션 종료 처리
                client.sendEvent(SESSION_ENDED, Map.of(
                    "reason", "force_logout",
                    "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
                ));

                // 연결 종료
                client.disconnect();

            } catch (Exception e) {
                log.error("Force login error", e);
                client.sendEvent(ERROR, Map.of(
                    "message", "세션 종료 중 오류가 발생했습니다."
                ));
            }
        };
    }

    // 기존 세션 유지 처리 (노드 버전과 동일)
    @SuppressWarnings("rawtypes")
    private DataListener<Map> onKeepExistingSession() {
        return (client, data, ackSender) -> {
            try {
                String userId = getUserId(client);
                if (userId == null) return;

                @SuppressWarnings("unchecked")
                Map<String, Object> requestData = (Map<String, Object>) data;
                String token = requestData != null ? (String) requestData.get("token") : null;
                
                if (token == null || token.isBlank()) {
                    client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                    return;
                }

                try {
                    if (!jwtUtil.validateToken(token)) {
                        throw new IllegalArgumentException("Token validation failed");
                    }

                    String tokenUserId = jwtUtil.extractSubject(token);
                    if (tokenUserId == null || !tokenUserId.equals(userId)) {
                        throw new IllegalArgumentException("Token user mismatch");
                    }
                } catch (Exception validationError) {
                    log.warn("Invalid token for keep_existing_session: {}", validationError.getMessage());
                    client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                    return;
                }

                PendingDuplicateLogin pending = pendingDuplicateLogins.remove(userId);
                if (pending == null) {
                    log.debug("No pending duplicate login found for keep_existing_session - userId: {}", userId);
                    return;
                }

                pending.cancelTimeout();

                SocketIOClient newClient = pending.getNewClient();
                if (newClient != null) {
                    newClient.sendEvent(SESSION_ENDED, Map.of(
                        "reason", "keep_existing",
                        "message", "기존 세션을 유지합니다."
                    ));
                    socketClients.remove(pending.getNewSocketId());
                    newClient.disconnect();
                }

                if (pending.getExistingSocketId() != null) {
                    connectedUsers.put(userId, pending.getExistingSocketId());
                }

                log.info("Keep existing session acknowledged - userId: {}", userId);

            } catch (Exception e) {
                log.error("Keep existing session error", e);
                client.sendEvent(ERROR, Map.of(
                    "message", "세션 처리 중 오류가 발생했습니다."
                ));
            }
        };
    }

    private static final class PendingDuplicateLogin {
        private final SocketIOClient existingClient;
        private final String existingSocketId;
        private final SocketIOClient newClient;
        private final String newSocketId;
        private ScheduledFuture<?> timeoutTask;

        private PendingDuplicateLogin(SocketIOClient existingClient, String existingSocketId,
                                      SocketIOClient newClient, String newSocketId) {
            this.existingClient = existingClient;
            this.existingSocketId = existingSocketId;
            this.newClient = newClient;
            this.newSocketId = newSocketId;
        }

        private void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
            this.timeoutTask = timeoutTask;
        }

        private void cancelTimeout() {
            if (timeoutTask != null) {
                timeoutTask.cancel(true);
            }
        }

        private boolean involves(String socketId) {
            return socketId != null
                && (socketId.equals(existingSocketId) || socketId.equals(newSocketId));
        }

        private String getExistingSocketId() {
            return existingSocketId;
        }

        private String getNewSocketId() {
            return newSocketId;
        }

        private SocketIOClient getNewClient() {
            return newClient;
        }
    }

    // 헬퍼 메서드들
    private java.util.concurrent.CompletableFuture<FetchMessagesResponse> loadMessagesWithRetry(
            SocketIOClient client, String roomId, LocalDateTime before, String retryKey) {

        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                Integer currentRetries = messageLoadRetries.get(retryKey);
                if (currentRetries != null && currentRetries >= MAX_RETRIES) {
                    throw new RuntimeException("최대 재시도 횟수를 초과했습니다.");
                }

                Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("timestamp").ascending());
                LocalDateTime beforeTime = before != null ? before : LocalDateTime.now();

                Page<Message> messagePage = messageRepository.findByRoomIdAndIsDeletedAndTimestampBefore(
                        roomId, false, beforeTime, pageable);

                List<Message> messages = messagePage.getContent();

                // 사용자 정보 조회
                Set<String> senderIds = messages.stream()
                        .map(Message::getSenderId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

                // 메시지 응답 생성
                List<MessageResponse> messageResponses = messages.stream()
                        .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                        .collect(Collectors.toList());

                // 읽음 상태 업데이트 (비동기)
                if (!messages.isEmpty()) {
                    String userId = getUserId(client);
                    if (userId != null) {
                        updateReadStatus(messages, userId);
                    }
                }

                log.debug("Fetch previous messages - roomId: {}, before: {}, count: {}, hasNext: {}",
                    roomId, before, messageResponses.size(), messagePage.hasNext());

                // oldestTimestamp를 ISO_INSTANT 형식으로 변환
                String oldestTimestampStr = null;
                if (!messages.isEmpty() && messages.getFirst().getTimestamp() != null) {
                    Instant instant = messages.getFirst().getTimestamp()
                        .atZone(ZoneId.systemDefault())
                        .toInstant();
                    oldestTimestampStr = instant.toString();
                }

                messageLoadRetries.remove(retryKey);
                return new FetchMessagesResponse(
                    messageResponses,
                    messagePage.hasNext(),
                    oldestTimestampStr
                );

            } catch (Exception e) {
                Integer currentRetries = messageLoadRetries.getOrDefault(retryKey, 0);

                if (currentRetries < MAX_RETRIES) {
                    messageLoadRetries.put(retryKey, currentRetries + 1);
                    int delay = Math.min(RETRY_DELAY * (int) Math.pow(2, currentRetries), 10000);

                    log.debug("Retrying message load for room {} (attempt {}/{})",
                        roomId, currentRetries + 1, MAX_RETRIES);

                    try {
                        Thread.sleep(delay);
                        return loadMessagesWithRetry(client, roomId, before, retryKey).get();
                    } catch (InterruptedException | java.util.concurrent.ExecutionException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                }

                messageLoadRetries.remove(retryKey);
                throw new RuntimeException(e.getMessage(), e);
            }
        })
        .orTimeout(MESSAGE_LOAD_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
        .exceptionally(throwable -> {
            Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                ? throwable.getCause()
                : throwable;

            if (cause instanceof java.util.concurrent.TimeoutException timeout) {
                log.debug("Message load timeout - roomId: {}, before: {}", roomId, before);
                throw new RuntimeException("Message loading timed out", timeout);
            }

            throw new RuntimeException(cause);
        });
    }

    private void updateReadStatus(List<Message> messages, String userId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
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
                        message.setReaders(new java.util.ArrayList<>());
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

    private void sendSystemMessage(String roomId, String content) {
        try {
            Message systemMessage = new Message();
            systemMessage.setRoomId(roomId);
            systemMessage.setContent(content);
            systemMessage.setType(MessageType.system);
            systemMessage.setTimestamp(LocalDateTime.now());
            systemMessage.setMentions(new ArrayList<>());
            systemMessage.setIsDeleted(false);
            systemMessage.setReactions(new HashMap<>());
            systemMessage.setReaders(new ArrayList<>());
            systemMessage.setMetadata(new HashMap<>());

            Message savedMessage = messageRepository.save(systemMessage);
            MessageResponse response = mapToMessageResponse(savedMessage, null);

            socketIOServer.getRoomOperations(roomId)
                    .sendEvent(MESSAGE, response);

        } catch (Exception e) {
            log.error("Error sending system message", e);
        }
    }

    private void broadcastParticipantList(String roomId) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room != null) {
                List<User> participants = userRepository.findAllById(room.getParticipantIds());

                var participantList = participants.stream()
                        .map(user -> Map.of(
                            "id", user.getId(),
                            "name", user.getName(),
                            "email", user.getEmail(),
                            "profileImage", user.getProfileImage() != null ? user.getProfileImage() : ""
                        ))
                        .collect(Collectors.toList());

                socketIOServer.getRoomOperations(roomId)
                        .sendEvent(PARTICIPANTS_UPDATE, participantList);
            }
        } catch (Exception e) {
            log.error("Error broadcasting participant list", e);
        }
    }

    private MessageResponse mapToMessageResponse(Message message, User sender) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.toTimestampMillis())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ? message.getReactions() : new java.util.HashMap<>())
                .readers(message.getReaders() != null ? message.getReaders() : new java.util.ArrayList<>());
        // mentions 필드는 현재 지원하지 않는다.

        if (sender != null) {
            builder.sender(UserResponse.builder()
                    .id(sender.getId())
                    .name(sender.getName())
                    .email(sender.getEmail())
                    .profileImage(sender.getProfileImage())
                    .build());
        }

        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId()).ifPresent(file -> builder.file(FileResponse.builder()
                    .id(file.getId())
                    .filename(file.getFilename())
                    .originalname(file.getOriginalname())
                    .mimetype(file.getMimetype())
                    .size(file.getSize())
                    .build()));
        }

        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }

    private FetchMessagesResponse loadInitialMessages(String roomId, String userId) {
        java.util.concurrent.CompletableFuture<FetchMessagesResponse> future =
            java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("timestamp").descending());

                    Page<Message> messagePage = messageRepository.findByRoomIdAndIsDeletedAndTimestampBefore(
                            roomId, false, LocalDateTime.now(), pageable);

                    List<Message> messages = messagePage.getContent();

                    boolean hasMore = messages.size() > BATCH_SIZE;
                    List<Message> resultMessages = messages.size() > BATCH_SIZE
                        ? messages.subList(0, BATCH_SIZE)
                        : messages;

                    List<Message> sortedMessages = resultMessages.stream()
                        .sorted(java.util.Comparator.comparing(Message::getTimestamp))
                        .toList();

                    if (userId != null && !sortedMessages.isEmpty()) {
                        java.util.concurrent.CompletableFuture.runAsync(() -> updateReadStatus(sortedMessages, userId));
                    }

                    Set<String> senderIds = sortedMessages.stream()
                            .map(Message::getSenderId)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());

                    Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                            .collect(Collectors.toMap(User::getId, user -> user));

                    List<MessageResponse> messageResponses = sortedMessages.stream()
                            .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                            .collect(Collectors.toList());

                    // oldestTimestamp를 ISO_INSTANT 형식으로 변환
                    String oldestTimestampStr = null;
                    if (!sortedMessages.isEmpty() && sortedMessages.getFirst().getTimestamp() != null) {
                        Instant instant = sortedMessages.getFirst().getTimestamp()
                            .atZone(ZoneId.systemDefault())
                            .toInstant();
                        oldestTimestampStr = instant.toString();
                    }

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
                        .messages(new java.util.ArrayList<>())
                        .hasMore(false)
                        .oldestTimestamp(null)
                        .build();
                }
            });

        try {
            return future
                .orTimeout(MESSAGE_LOAD_TIMEOUT, java.util.concurrent.TimeUnit.MILLISECONDS)
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

    // client에서 UserDto 가져오는 헬퍼 메서드
    private UserResponse getUserDto(SocketIOClient client) {
        return client.get("user");
    }
    
    // client에서 userId 가져오는 헬퍼 메서드
    private String getUserId(SocketIOClient client) {
        UserResponse user = getUserDto(client);
        return user != null ? user.getId() : null;
    }
    
    // client에서 userName 가져오는 헬퍼 메서드
    private String getUserName(SocketIOClient client) {
        UserResponse user = getUserDto(client);
        return user != null ? user.getName() : null;
    }
    
}
