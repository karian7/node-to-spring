package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DataListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import com.example.chatapp.dto.*;
import com.example.chatapp.dto.ai.AiMessageChunk;
import com.example.chatapp.dto.ai.AiMessageComplete;
import com.example.chatapp.dto.ai.AiMessageStart;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.AiService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Socket.IO Chat Handler
 * Mimics Node.js backend's chat.js functionality with identical event names and auth handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOChatHandler {

    private final SocketIOServer socketIOServer;
    private final SocketIOAuthHandler authHandler;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final AiService aiService;

    // Online users management (similar to Node.js connectedUsers)
    private final Map<String, String> connectedUsers = new ConcurrentHashMap<>(); // userId -> socketId
    private final Map<String, SocketIOClient> socketClients = new ConcurrentHashMap<>(); // socketId -> client

    @PostConstruct
    public void initializeEventHandlers() {
        // Connection event (similar to Node.js io.on('connection'))
        socketIOServer.addConnectListener(onConnect());
        socketIOServer.addDisconnectListener(onDisconnect());

        // Chat events (same as Node.js backend)
        socketIOServer.addEventListener("sendMessage", SendMessageRequest.class, onSendMessage());
        socketIOServer.addEventListener("joinRoom", RoomIdRequest.class, onJoinRoom());
        socketIOServer.addEventListener("leaveRoom", RoomIdRequest.class, onLeaveRoom());
        socketIOServer.addEventListener("fetchPreviousMessages", FetchMessagesRequest.class, onFetchPreviousMessages());
        socketIOServer.addEventListener("markMessagesAsRead", MarkAsReadRequest.class, onMarkMessagesAsRead());
        socketIOServer.addEventListener("messageReaction", MessageReactionRequest.class, onMessageReaction());

        // Notification events (same as Node.js backend)
        socketIOServer.addEventListener("typing.start", TypingRequest.class, onTypingStart());
        socketIOServer.addEventListener("typing.stop", TypingRequest.class, onTypingStop());
        socketIOServer.addEventListener("users.online", Object.class, onGetOnlineUsers());
        socketIOServer.addEventListener("user.activity", Object.class, onUpdateUserActivity());

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
                            existingClient.sendEvent("duplicate_login", Map.of(
                                "type", "new_login_attempt",
                                "deviceInfo", client.getHandshakeData().getHttpHeaders().get("User-Agent"),
                                "ipAddress", client.getRemoteAddress().toString(),
                                "timestamp", System.currentTimeMillis()
                            ));

                            // Disconnect existing client after delay
                            new Thread(() -> {
                                try {
                                    Thread.sleep(10000); // 10 second delay like Node.js
                                    existingClient.sendEvent("session_ended", Map.of(
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

                    // Broadcast user offline status (similar to Node.js)
                    socketIOServer.getBroadcastOperations().sendEvent("user.status", Map.of(
                        "userId", userId,
                        "isOnline", false,
                        "timestamp", LocalDateTime.now()
                    ));
                }
            } catch (Exception e) {
                log.error("Error handling Socket.IO disconnection", e);
            }
        };
    }

    @Transactional
    private DataListener<SendMessageRequest> onSendMessage() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");
                String userEmail = client.getHandshakeData().getHttpHeaders().get("socket.user.email");

                User sender = userRepository.findById(userId).orElse(null);
                if (sender == null) {
                    client.sendEvent("error", "User not found");
                    return;
                }

                Room room = roomRepository.findById(data.getRoomId()).orElse(null);
                if (room == null) {
                    client.sendEvent("error", "Room not found");
                    return;
                }

                // Check room participation
                if (!room.getParticipantIds().contains(userId)) {
                    client.sendEvent("error", "채팅방에 참여하지 않은 사용자입니다.");
                    return;
                }

                // Create message (same logic as Node.js)
                Message message = new Message();
                message.setSenderId(userId);
                message.setRoomId(data.getRoomId());
                message.setContent(data.getContent());
                message.setType(data.getType());
                message.setMentions(data.getMentions());
                message.setTimestamp(LocalDateTime.now());
                message.setReactions(new java.util.HashMap<>());

                // Handle file message
                if (data.getType() == MessageType.FILE && data.getFileId() != null) {
                    com.example.chatapp.model.File file = fileRepository.findById(data.getFileId()).orElse(null);
                    if (file != null && file.getUploadedBy().equals(userId)) {
                        message.setFileId(file.getId());
                        message.setMetadata(Message.FileMetadata.builder()
                                .fileType(file.getMimetype())
                                .fileSize(file.getSize())
                                .originalName(file.getOriginalname())
                                .build());
                    }
                }

                Message savedMessage = messageRepository.save(message);

                // Create response (same as Node.js)
                MessageResponse messageResponse = mapToMessageResponse(savedMessage, sender);

                // Broadcast to room (same as Node.js)
                socketIOServer.getRoomOperations("room:" + data.getRoomId())
                        .sendEvent("message", messageResponse);

                // Send delivery confirmation
                client.sendEvent("delivery", new DeliveryConfirmation(
                        savedMessage.getId(), savedMessage.getTimestamp(), "DELIVERED"));

                // Handle mentions (same as Node.js)
                if (data.getMentions() != null && !data.getMentions().isEmpty()) {
                    handleMentions(data.getMentions(), messageResponse, room);
                }

                // Handle AI mentions (same as Node.js)
                handleAiMentions(savedMessage);

            } catch (Exception e) {
                log.error("Error handling sendMessage", e);
                client.sendEvent("error", "메시지 전송 중 오류가 발생했습니다.");
            }
        };
    }

    @Transactional
    private DataListener<RoomIdRequest> onJoinRoom() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent("error", "User not found");
                    return;
                }

                Room room = roomRepository.findById(data.roomId()).orElse(null);
                if (room == null) {
                    client.sendEvent("error", "Room not found: " + data.roomId());
                    return;
                }

                room.getParticipantIds().add(userId);
                roomRepository.save(room);

                // Join socket room
                client.joinRoom("room:" + data.roomId());

                log.info("User {} joined room {}", userName, room.getName());

                // Send system message (same as Node.js)
                sendSystemMessage(data.roomId(), userName + "님이 입장하였습니다.");

                // Broadcast updated participant list
                broadcastParticipantList(data.roomId());

            } catch (Exception e) {
                log.error("Error handling joinRoom", e);
                client.sendEvent("error", "방 입장 중 오류가 발생했습니다.");
            }
        };
    }

    @Transactional
    private DataListener<RoomIdRequest> onLeaveRoom() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                User user = userRepository.findById(userId).orElse(null);
                Room room = roomRepository.findById(data.roomId()).orElse(null);

                if (user != null && room != null) {
                    room.getParticipantIds().remove(userId);
                    roomRepository.save(room);

                    // Leave socket room
                    client.leaveRoom("room:" + data.roomId());

                    log.info("User {} left room {}", userName, room.getName());

                    // Send system message
                    sendSystemMessage(data.roomId(), userName + "님이 퇴장하였습니다.");

                    // Broadcast updated participant list
                    broadcastParticipantList(data.roomId());
                }

            } catch (Exception e) {
                log.error("Error handling leaveRoom", e);
                client.sendEvent("error", "방 퇴장 중 오류가 발생했습니다.");
            }
        };
    }

    private DataListener<FetchMessagesRequest> onFetchPreviousMessages() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                final int BATCH_SIZE = 30;

                // Check room access
                Room room = roomRepository.findById(data.roomId()).orElse(null);
                if (room == null || !room.getParticipantIds().contains(userId)) {
                    client.sendEvent("error", "채팅방 접근 권한이 없습니다.");
                    return;
                }

                // Create pageable
                Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("timestamp").descending());
                LocalDateTime before = data.before() != null ? data.before() : LocalDateTime.now();

                // Fetch messages
                Page<Message> messagePage = messageRepository.findByRoomIdAndTimestampBefore(
                        data.roomId(), before, pageable);

                List<Message> messages = messagePage.getContent();

                // Fetch users for messages
                Set<String> senderIds = messages.stream()
                        .map(Message::getSenderId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

                Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

                // Map to response
                List<MessageResponse> messageResponses = messages.stream()
                        .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                        .collect(Collectors.toList());

                FetchMessagesResponse response = new FetchMessagesResponse(messageResponses, messagePage.hasNext());

                // Send response to client
                client.sendEvent("messages.history", response);

            } catch (Exception e) {
                log.error("Error handling fetchPreviousMessages", e);
                client.sendEvent("error", "메시지 로드 중 오류가 발생했습니다.");
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
                        .sendEvent("messages.read", response);

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
                        .sendEvent("messages.reaction", response);

            } catch (Exception e) {
                log.error("Error handling messageReaction", e);
            }
        };
    }

    // Notification event handlers
    private DataListener<TypingRequest> onTypingStart() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId == null || userName == null) {
                    client.sendEvent("error", "User authentication required");
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent("error", "User not found: " + userId);
                    return;
                }

                TypingEvent typingEvent = new TypingEvent(
                        user.getId(),
                        user.getName(),
                        data.roomId(),
                        true,
                        LocalDateTime.now()
                );

                // Broadcast to room participants (equivalent to STOMP's convertAndSend)
                socketIOServer.getRoomOperations("room:" + data.roomId())
                        .sendEvent("typing", typingEvent);

                log.debug("User {} started typing in room {}", user.getName(), data.roomId());

            } catch (Exception e) {
                log.error("Error handling typing start", e);
                client.sendEvent("error", "Failed to handle typing start");
            }
        };
    }

    private DataListener<TypingRequest> onTypingStop() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId == null || userName == null) {
                    client.sendEvent("error", "User authentication required");
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent("error", "User not found: " + userId);
                    return;
                }

                TypingEvent typingEvent = new TypingEvent(
                        user.getId(),
                        user.getName(),
                        data.roomId(),
                        false,
                        LocalDateTime.now()
                );

                // Broadcast to room participants (equivalent to STOMP's convertAndSend)
                socketIOServer.getRoomOperations("room:" + data.roomId())
                        .sendEvent("typing", typingEvent);

                log.debug("User {} stopped typing in room {}", user.getName(), data.roomId());

            } catch (Exception e) {
                log.error("Error handling typing stop", e);
                client.sendEvent("error", "Failed to handle typing stop");
            }
        };
    }

    private DataListener<Object> onGetOnlineUsers() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");

                if (userId == null) {
                    client.sendEvent("error", "User authentication required");
                    return;
                }

                // Get online user count from our connected users map
                int onlineCount = connectedUsers.size();
                OnlineUsersResponse response = new OnlineUsersResponse(onlineCount, LocalDateTime.now());

                // Send response directly to the requesting client (equivalent to STOMP's convertAndSendToUser)
                client.sendEvent("users.online", response);

                log.debug("Online users count sent to user {}: {}", userId, onlineCount);

            } catch (Exception e) {
                log.error("Error getting online users", e);
                client.sendEvent("error", "Failed to get online users");
            }
        };
    }

    private DataListener<Object> onUpdateUserActivity() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId == null) {
                    client.sendEvent("error", "User authentication required");
                    return;
                }

                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    client.sendEvent("error", "User not found: " + userId);
                    return;
                }

                // Update user's last activity timestamp
                user.setLastActive(LocalDateTime.now());
                userRepository.save(user);

                log.debug("Updated activity for user: {}", user.getName());

                // Optional: Send confirmation back to client
                client.sendEvent("user.activity.updated", Map.of(
                    "userId", userId,
                    "timestamp", LocalDateTime.now()
                ));

            } catch (Exception e) {
                log.error("Error updating user activity", e);
                client.sendEvent("error", "Failed to update user activity");
            }
        };
    }

    // DTO record classes (moved from deleted NotificationController)
    public record TypingRequest(String roomId) {
    }

    public record TypingEvent(
        String userId,
        String userName,
        String roomId,
        boolean isTyping,
        LocalDateTime timestamp
    ) {
    }

    public record OnlineUsersResponse(int count, LocalDateTime timestamp) {
    }

    // Helper methods (same as Node.js backend)
    private MessageResponse mapToMessageResponse(Message message, User sender) {
        UserSummaryResponse senderSummary = null;
        if (sender != null) {
            senderSummary = new UserSummaryResponse(
                    sender.getId(),
                    sender.getName(),
                    sender.getEmail(),
                    sender.getProfileImage()
            );
        }

        return MessageResponse.builder()
                .id(message.getId())
                .roomId(message.getRoomId())
                .content(message.getContent())
                .sender(senderSummary)
                .type(message.getType())
                .file(null) // File response can be added later
                .aiType(message.getAiType())
                .mentions(message.getMentions())
                .timestamp(message.getTimestamp())
                .readCount(message.getReadCount())
                .isDeleted(message.isDeleted())
                .build();
    }

    private void handleMentions(List<String> mentionedUserIds, MessageResponse message, Room room) {
        for (String mentionedUserId : mentionedUserIds) {
            if (isUserOnline(mentionedUserId)) {
                SocketIOClient mentionedClient = getClientByUserId(mentionedUserId);
                if (mentionedClient != null) {
                    mentionedClient.sendEvent("mention", new MentionNotification(
                            message.getId(),
                            message.getRoomId(),
                            room.getName(),
                            message.getSender().getName(),
                            message.getContent(),
                            LocalDateTime.now()
                    ));
                }
            }
        }
    }

    private void handleAiMentions(Message originalMessage) {
        // AI mention handling logic (same as Node.js backend)
        String content = originalMessage.getContent();
        List<String> mentions = extractAIMentions(content);

        for (String mention : mentions) {
            String query = content.replaceAll("(?i)@(wayneAI|consultingAI)", "").trim();
            String tempId = mention + "-" + System.currentTimeMillis();
            StringBuilder accumulatedContent = new StringBuilder();

            // Convert mention to enum name format
            String enumMention = mention.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
            AiType aiType = AiType.valueOf(enumMention);

            aiService.generateResponse(query, mention)
                .doOnSubscribe(subscription -> {
                    // Send AI_MESSAGE_START
                    AiMessageStart startEvent = new AiMessageStart(tempId, aiType, LocalDateTime.now());
                    socketIOServer.getRoomOperations("room:" + originalMessage.getRoomId())
                            .sendEvent("ai.stream.start", startEvent);
                })
                .doOnError(error -> {
                    log.error("Error generating AI response", error);
                    socketIOServer.getRoomOperations("room:" + originalMessage.getRoomId())
                            .sendEvent("ai.stream.error", "Error generating response");
                })
                .doOnComplete(() -> {
                    // Save the final message and send AI_MESSAGE_COMPLETE
                    Message aiMessage = new Message();
                    aiMessage.setRoomId(originalMessage.getRoomId());
                    aiMessage.setContent(accumulatedContent.toString());
                    aiMessage.setType(MessageType.AI);
                    aiMessage.setAiType(aiType);
                    aiMessage.setTimestamp(LocalDateTime.now());
                    Message savedAiMessage = messageRepository.save(aiMessage);

                    AiMessageComplete completeEvent = new AiMessageComplete(
                            tempId,
                            savedAiMessage.getId(),
                            savedAiMessage.getContent(),
                            savedAiMessage.getAiType(),
                            savedAiMessage.getTimestamp(),
                            true,
                            query,
                            savedAiMessage.getReactions()
                    );
                    socketIOServer.getRoomOperations("room:" + originalMessage.getRoomId())
                            .sendEvent("ai.stream.complete", completeEvent);
                })
                .subscribe(chunk -> {
                    // Send AI_MESSAGE_CHUNK
                    accumulatedContent.append(chunk);
                    AiMessageChunk chunkEvent = new AiMessageChunk(
                            tempId,
                            chunk,
                            accumulatedContent.toString(),
                            false,
                            LocalDateTime.now(),
                            aiType,
                            false
                    );
                    socketIOServer.getRoomOperations("room:" + originalMessage.getRoomId())
                            .sendEvent("ai.stream.chunk", chunkEvent);
                });
        }
    }

    private List<String> extractAIMentions(String content) {
        if (content == null) return java.util.Collections.emptyList();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)@(wayneAI|consultingAI)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        List<String> mentions = new java.util.ArrayList<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions.stream().distinct().collect(Collectors.toList());
    }

    private boolean isUserOnline(String userId) {
        return connectedUsers.containsKey(userId);
    }

    private SocketIOClient getClientByUserId(String userId) {
        String socketId = connectedUsers.get(userId);
        return socketId != null ? socketClients.get(socketId) : null;
    }

    // Helper methods (same as Node.js backend) - moved here to avoid compilation errors
    private void sendSystemMessage(String roomId, String content) {
        Message message = new Message();
        message.setRoomId(roomId);
        message.setContent(content);
        message.setType(MessageType.SYSTEM);
        message.setTimestamp(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        MessageResponse messageResponse = mapToMessageResponse(savedMessage, null);
        socketIOServer.getRoomOperations("room:" + roomId).sendEvent("message", messageResponse);
    }

    private void broadcastParticipantList(String roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) return;

        List<UserSummaryResponse> participants = userRepository.findAllById(room.getParticipantIds()).stream()
                .map(p -> new UserSummaryResponse(p.getId(), p.getName(), p.getEmail(), p.getProfileImage()))
                .collect(Collectors.toList());

        ParticipantListResponse response = new ParticipantListResponse(roomId, participants);
        socketIOServer.getRoomOperations("room:" + roomId).sendEvent("participants", response);
    }
}
