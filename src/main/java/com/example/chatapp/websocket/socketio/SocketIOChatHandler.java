package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
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
import com.example.chatapp.websocket.socketio.handler.ChatMessageHandler;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
 * Mimics Node.js backend's chat.js functionality with identical event names and auth handling
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

    // Online users management (similar to Node.js connectedUsers)
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>(); // userId -> socketId
    private final Map<String, SocketIOClient> socketClients = new ConcurrentHashMap<>(); // socketId -> client

    private final Map<String, Boolean> messageQueues = new ConcurrentHashMap<>();
    private final Map<String, Integer> messageLoadRetries = new ConcurrentHashMap<>();

    // User rooms management (similar to Node.js userRooms)
    private final Map<String, String> userRooms = new ConcurrentHashMap<>(); // userId -> roomId
    // Streaming sessions management (similar to Node.js streamingSessions)
    private final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();

    // 노드 버전과 동일한 상수들
    private static final int BATCH_SIZE = 30;
    private static final int LOAD_DELAY = 300;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY = 2000;
    private static final int DUPLICATE_LOGIN_TIMEOUT = 10000;

    @PostConstruct
    public void initializeEventHandlers() {
        // Connection event (similar to Node.js io.on('connection'))
        socketIOServer.addConnectListener(onConnect());
        socketIOServer.addDisconnectListener(onDisconnect());

        // Chat events (same as Node.js backend) - Object로 타입 변경
        socketIOServer.addEventListener(CHAT_MESSAGE, Map.class, chatMessageHandler.getListener());
        socketIOServer.addEventListener(JOIN_ROOM, String.class, onJoinRoom());
        socketIOServer.addEventListener(LEAVE_ROOM, String.class, onLeaveRoom());
        socketIOServer.addEventListener(FETCH_PREVIOUS_MESSAGES, FetchMessagesRequest.class, onFetchPreviousMessagesWithRetry());
        socketIOServer.addEventListener(MARK_MESSAGES_AS_READ, MarkAsReadRequest.class, onMarkMessagesAsRead());
        socketIOServer.addEventListener(MESSAGE_REACTION, MessageReactionRequest.class, onMessageReaction());
        socketIOServer.addEventListener(FORCE_LOGIN, Map.class, onForceLogin());

        log.info("Socket.IO event handlers initialized");
    }

    private ConnectListener onConnect() {
        return client -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId != null) {
                    // Handle duplicate connections (similar to Node.js handleDuplicateLogin)
                    String existingSocketId = connectedUsers.get(userId);
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

                            // Disconnect existing client after delay
                            new Thread(() -> {
                                try {
                                    Thread.sleep(DUPLICATE_LOGIN_TIMEOUT); // 10 second delay like Node.js
                                    existingClient.sendEvent(SESSION_ENDED, Map.of(
                                        "reason", "duplicate_login",
                                        "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
                                    ));
                                    existingClient.disconnect();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }).start();
                        }
                    }

                    // Store new connection
                    connectedUsers.put(userId, client.getSessionId().toString());
                    socketClients.put(client.getSessionId().toString(), client);

                    log.info("Socket.IO user connected: {} ({})", userName, userId);

                    // Join user to their personal room for direct messages
                    client.joinRoom("user:" + userId);

                }
            } catch (Exception e) {
                log.error("Error handling Socket.IO connection", e);
            }
        };
    }

    private DisconnectListener onDisconnect() {
        return client -> {
            try {
                String socketId = client.getSessionId().toString();
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId != null) {
                    connectedUsers.remove(userId);
                    socketClients.remove(socketId);

                    log.info("Socket.IO user disconnected: {} ({})", userName, userId);
                }
            } catch (Exception e) {
                log.error("Error handling Socket.IO disconnection", e);
            }
        };
    }


    public DataListener<String> onJoinRoom() {
        return (client, roomId, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId == null) {
                    client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "Unauthorized"));
                    return;
                }

                // 이미 해당 방에 참여 중인지 확인
                String currentRoom = userRooms.get(userId);
                if (roomId.equals(currentRoom)) {
                    log.debug("User {} already in room {}", userId, roomId);
                    client.joinRoom("room:" + roomId);
                    client.sendEvent(JOIN_ROOM_SUCCESS, Map.of("roomId", roomId));
                    return;
                }

                // 기존 방에서 나가기
                if (currentRoom != null) {
                    log.debug("User {} leaving current room {}", userId, currentRoom);
                    client.leaveRoom("room:" + currentRoom);
                    userRooms.remove(userId);

                    // 기존 방에 퇴장 알림
                    socketIOServer.getRoomOperations("room:" + currentRoom)
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

                room.getParticipantIds().add(userId);
                room = roomRepository.save(room);

                // Join socket room
                client.joinRoom("room:" + roomId);
                userRooms.put(userId, roomId);

                // 입장 메시지 생성
                Message joinMessage = Message.builder()
                    .roomId(roomId)
                    .content(userName + "이 입장하였습니다.")
                    .type(MessageType.SYSTEM)
                    .timestamp(LocalDateTime.now())
                    .build();

                joinMessage = messageRepository.save(joinMessage);

                // 초기 메시지 로드
                FetchMessagesResponse messageLoadResult = loadInitialMessages(roomId);

                // 참가자 정보 조회 (with profileImage)
                List<User> participantUsers = userRepository.findAllById(room.getParticipantIds());
                List<UserDto> participants = participantUsers.stream()
                    .map(this::mapToUserDto)
                    .collect(Collectors.toList());

                // 활성 스트리밍 메시지 조회
                List<ActiveStreamResponse> activeStreams = streamingSessions.values().stream()
                    .filter(session -> roomId.equals(session.getRoomId()))
                    .map(session -> ActiveStreamResponse.builder()
                        .id(session.getMessageId())
                        .type("ai")
                        .aiType(session.getAiType())
                        .content(session.getContent())
                        .timestamp(session.getTimestamp())
                        .isStreaming(true)
                        .build())
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
                socketIOServer.getRoomOperations("room:" + roomId)
                    .sendEvent(MESSAGE, mapToMessageResponse(joinMessage, null));

                // 참가자 목록 업데이트 브로드캐스트
                socketIOServer.getRoomOperations("room:" + roomId)
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
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                User user = userRepository.findById(userId).orElse(null);
                Room room = roomRepository.findById(roomId).orElse(null);

                if (user != null && room != null) {
                    room.getParticipantIds().remove(userId);
                    roomRepository.save(room);

                    // Leave socket room
                    client.leaveRoom("room:" + roomId);

                    log.info("User {} left room {}", userName, room.getName());

                    // Send system message
                    sendSystemMessage(roomId, userName + "님이 퇴장하였습니다.");

                    // Broadcast updated participant list
                    broadcastParticipantList(roomId);
                }

            } catch (Exception e) {
                log.error("Error handling leaveRoom", e);
                client.sendEvent(ERROR, "방 퇴장 중 오류가 발생했습니다.");
            }
        };
    }

    // 향상된 메시지 로드 기능 (노드 버전과 동일)
    private DataListener<FetchMessagesRequest> onFetchPreviousMessagesWithRetry() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
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
                            "type", "LOAD_ERROR",
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
                                    "type", "LOAD_ERROR",
                                    "message", throwable.getMessage() != null ? throwable.getMessage() : "이전 메시지를 불러오는 중 오류가 발생했습니다."
                            ));
                            messageQueues.remove(queueKey);
                            return null;
                        });

            } catch (Exception e) {
                log.error("Error handling fetchPreviousMessages", e);
                client.sendEvent(ERROR, Map.of(
                        "type", "LOAD_ERROR",
                        "message", "메시지 로드 중 오류가 발생했습니다."
                ));
            }
        };
    }

    private DataListener<MarkAsReadRequest> onMarkMessagesAsRead() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) return;

                List<Message> messages = messageRepository.findAllById(data.getMessageIds());

                Message.MessageReader readerInfo = Message.MessageReader.builder()
                        .userId(userId)
                        .readAt(LocalDateTime.now())
                        .build();

                messages.forEach(message -> {
                    if (message.getReaders() == null) {
                        message.setReaders(new java.util.ArrayList<>());
                    }
                    boolean alreadyRead = message.getReaders().stream()
                            .anyMatch(r -> r.getUserId().equals(userId));
                    if (!alreadyRead) {
                        message.getReaders().add(readerInfo);
                    }
                });

                messageRepository.saveAll(messages);

                MessagesReadResponse response = new MessagesReadResponse(userId, data.getMessageIds());

                // Broadcast to room
                socketIOServer.getRoomOperations("room:" + data.getRoomId())
                        .sendEvent(MESSAGES_READ, response);

            } catch (Exception e) {
                log.error("Error handling markMessagesAsRead", e);
            }
        };
    }

    private DataListener<MessageReactionRequest> onMessageReaction() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");

                Message message = messageRepository.findById(data.getMessageId()).orElse(null);
                if (message == null) return;

                if (message.getReactions() == null) {
                    message.setReactions(new java.util.HashMap<>());
                }

                if ("add".equals(data.getType())) {
                    message.getReactions()
                            .computeIfAbsent(data.getReaction(), k -> new java.util.HashSet<>())
                            .add(userId);
                } else if ("remove".equals(data.getType())) {
                    message.getReactions()
                            .computeIfPresent(data.getReaction(), (k, v) -> {
                                v.remove(userId);
                                return v.isEmpty() ? null : v;
                            });
                }

                messageRepository.save(message);

                MessageReactionResponse response = new MessageReactionResponse(message.getId(), message.getReactions());

                // Broadcast to room
                socketIOServer.getRoomOperations("room:" + message.getRoomId())
                        .sendEvent(MESSAGE_REACTION_UPDATE, response);

            } catch (Exception e) {
                log.error("Error handling messageReaction", e);
            }
        };
    }

    // 강제 로그아웃 처리 (노드 버전과 동일) - Object 타입으로 수정
    @SuppressWarnings("rawtypes")
    private DataListener<Map> onForceLogin() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                if (userId == null) return;

                // data를 Map으로 캐스팅
                @SuppressWarnings("unchecked")
                Map<String, Object> requestData = (Map<String, Object>) data;
                String token = (String) requestData.get("token");
                if (token == null) {
                    client.sendEvent(ERROR, "Invalid token");
                    return;
                }

                // 토큰 검증 로직은 실제 JWT 검증으로 대체해야 함
                // 여기서는 간단히 세션 종료 처리만 구현

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

    // 헬퍼 메서드들
    private java.util.concurrent.CompletableFuture<FetchMessagesResponse> loadMessagesWithRetry(
            SocketIOClient client, String roomId, LocalDateTime before, String retryKey) {

        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                Integer currentRetries = messageLoadRetries.get(retryKey);
                if (currentRetries != null && currentRetries >= MAX_RETRIES) {
                    throw new RuntimeException("최대 재시도 횟수를 초과했습니다.");
                }

                // 실제 메시지 로드 로직
                Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("timestamp").ascending());
                LocalDateTime beforeTime = before != null ? before : LocalDateTime.now();

                Page<Message> messagePage = messageRepository.findByRoomIdAndTimestampBefore(
                        roomId, beforeTime, pageable);

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
                    String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                    if (userId != null) {
                        updateReadStatus(messages, userId);
                    }
                }

                messageLoadRetries.remove(retryKey);
                return new FetchMessagesResponse(
                    messageResponses,
                    messagePage.hasNext(),
                    !messages.isEmpty() ? messages.getFirst().getTimestamp() : null
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
            systemMessage.setType(MessageType.SYSTEM);
            systemMessage.setTimestamp(LocalDateTime.now());

            Message savedMessage = messageRepository.save(systemMessage);
            MessageResponse response = mapToMessageResponse(savedMessage, null);

            socketIOServer.getRoomOperations("room:" + roomId)
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

                socketIOServer.getRoomOperations("room:" + roomId)
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
                .timestamp(message.getTimestamp())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ? message.getReactions() : new java.util.HashMap<>())
                .readers(message.getReaders() != null ? message.getReaders() : new java.util.ArrayList<>())
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

    // 초기 메시지 로드 메서드 (Node.js의 loadMessages와 동일)
    private FetchMessagesResponse loadInitialMessages(String roomId) {
        try {
            Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("timestamp").descending());

            Page<Message> messagePage = messageRepository.findByRoomIdAndTimestampBefore(
                    roomId, LocalDateTime.now(), pageable);

            List<Message> messages = messagePage.getContent();

            // hasMore 계산 (Node.js와 동일한 로직)
            boolean hasMore = messages.size() > BATCH_SIZE;
            List<Message> resultMessages = messages.size() > BATCH_SIZE ?
                messages.subList(0, BATCH_SIZE) : messages;

            // 시간순으로 정렬 (Node.js와 동일)
            List<Message> sortedMessages = resultMessages.stream()
                .sorted(java.util.Comparator.comparing(Message::getTimestamp))
                .toList();

            // 사용자 정보 조회
            Set<String> senderIds = sortedMessages.stream()
                    .map(Message::getSenderId)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());

            Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                    .collect(Collectors.toMap(User::getId, user -> user));

            // 메시지 응답 생성
            List<MessageResponse> messageResponses = sortedMessages.stream()
                    .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                    .collect(Collectors.toList());

            LocalDateTime oldestTimestamp = !sortedMessages.isEmpty() ?
                sortedMessages.getFirst().getTimestamp() : null;

            return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .oldestTimestamp(oldestTimestamp)
                .build();

        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", roomId, e);
            return FetchMessagesResponse.builder()
                .messages(new java.util.ArrayList<>())
                .hasMore(false)
                .oldestTimestamp(null)
                .build();
        }
    }

    // User를 UserDto로 변환하는 헬퍼 메서드
    private UserDto mapToUserDto(User user) {
        return UserDto.builder()
            .id(user.getId())
            .name(user.getName())
            .email(user.getEmail())
            .profileImage(user.getProfileImage())
            .build();
    }
    
}

