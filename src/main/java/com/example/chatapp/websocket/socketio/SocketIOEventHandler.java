package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.service.MessageService;
import com.example.chatapp.service.RealTimeNotificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Socket.IO 실시간 통신 이벤트 핸들러
 * Node.js backend와 동일한 이벤트 체계 제공
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOEventHandler {

    private final SocketIOServer socketIOServer;
    private final MessageService messageService;
    private final RealTimeNotificationService notificationService;

    // 연결된 사용자 관리 (Node.js의 connectedUsers와 동일)
    private final ConcurrentHashMap<String, SocketIOClient> connectedUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userRooms = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        socketIOServer.addConnectListener(client -> {
//            try {
//                String userId = getUserIdFromClient(client);
//                String sessionId = getSessionIdFromClient(client);
//
//                if (userId != null) {
//                    // 기존 연결이 있다면 종료 (중복 로그인 방지)
//                    SocketIOClient existingClient = connectedUsers.get(userId);
//                    if (existingClient != null && !existingClient.getSessionId().equals(client.getSessionId())) {
//                        log.info("중복 로그인 감지, 기존 연결 종료: {}", userId);
//                        existingClient.sendEvent("force-disconnect", "다른 곳에서 로그인되었습니다.");
//                        existingClient.disconnect();
//                    }
//
//                    connectedUsers.put(userId, client);
//                    client.set("userId", userId);
//                    client.set("sessionId", sessionId);
//
//                    // 사용자 온라인 상태 알림
//                    notificationService.broadcastUserStatusChange(userId, "online");
//
//                    log.info("사용자 연결: {} (세션: {})", userId, sessionId);
//                    client.sendEvent("connected", "연결되었습니다.");
//                } else {
//                    log.warn("인증되지 않은 연결 시도: {}", client.getSessionId());
//                    client.sendEvent("auth-error", "인증이 필요합니다.");
//                    client.disconnect();
//                }
//
//            } catch (Exception e) {
//                log.error("클라이언트 연결 중 에러 발생", e);
//                client.sendEvent("connection-error", "연결 중 오류가 발생했습니다.");
//                client.disconnect();
//            }
        });
        socketIOServer.addDisconnectListener(client -> {
//            try {
//                String userId = client.get("userId");
//                String currentRoom = userRooms.get(userId);
//
//                if (userId != null) {
//                    // 룸에서 나가기
//                    if (currentRoom != null) {
//                        client.leaveRoom(currentRoom);
//                        userRooms.remove(userId);
//
//                        // 룸 참여자들에게 사용자 나감 알림
//                        socketIOServer.getRoomOperations(currentRoom)
//                                .sendEvent("user-left", userId);
//                    }
//
//                    connectedUsers.remove(userId);
//
//                    // 사용자 오프라인 상태 알림
//                    notificationService.broadcastUserStatusChange(userId, "offline");
//
//                    log.info("사용자 연결 해제: {}", userId);
//                }
//
//            } catch (Exception e) {
//                log.error("클라이언트 연결 해제 중 에러 발생", e);
//            }
        });

        // 메시지 관련 이벤트
        socketIOServer.addEventListener("join-room", String.class, this::onJoinRoom);
        socketIOServer.addEventListener("leave-room", String.class, this::onLeaveRoom);
        socketIOServer.addEventListener("send-message", MessageRequest.class, this::onSendMessage);
        socketIOServer.addEventListener("load-messages", LoadMessageRequest.class, this::onLoadMessages);

        // 실시간 상태 이벤트
        socketIOServer.addEventListener("typing-start", TypingRequest.class, this::onTypingStart);
        socketIOServer.addEventListener("typing-stop", TypingRequest.class, this::onTypingStop);
        socketIOServer.addEventListener("user-status", StatusRequest.class, this::onUserStatusChange);

        log.info("Socket.IO 이벤트 핸들러 초기화 완료");
    }

    /**
     * 룸 입장 이벤트
     */
    private void onJoinRoom(SocketIOClient client, String roomId, Object ackSender) {
        try {
            String userId = client.get("userId");
            String previousRoom = userRooms.get(userId);

            // 이전 룸에서 나가기
            if (previousRoom != null && !previousRoom.equals(roomId)) {
                client.leaveRoom(previousRoom);
                socketIOServer.getRoomOperations(previousRoom)
                        .sendEvent("user-left", userId);
            }

            // 새 룸 입장
            client.joinRoom(roomId);
            userRooms.put(userId, roomId);

            // 룸 참여자들에게 사용자 입장 알림
            socketIOServer.getRoomOperations(roomId)
                    .sendEvent("user-joined", userId);

            log.info("사용자 {} 룸 {} 입장", userId, roomId);
            client.sendEvent("room-joined", roomId);

        } catch (Exception e) {
            log.error("룸 입장 중 에러 발생", e);
            client.sendEvent("join-error", "룸 입장에 실패했습니다.");
        }
    }

    /**
     * 룸 나가기 이벤트
     */
    private void onLeaveRoom(SocketIOClient client, String roomId, Object ackSender) {
        try {
            String userId = client.get("userId");

            client.leaveRoom(roomId);
            userRooms.remove(userId);

            // 룸 참여자들에게 사용자 나감 알림
            socketIOServer.getRoomOperations(roomId)
                    .sendEvent("user-left", userId);

            log.info("사용자 {} 룸 {} 나가기", userId, roomId);
            client.sendEvent("room-left", roomId);

        } catch (Exception e) {
            log.error("룸 나가기 중 에러 발생", e);
            client.sendEvent("leave-error", "룸 나가기에 실패했습니다.");
        }
    }

    /**
     * 메시지 전송 이벤트
     */
    private void onSendMessage(SocketIOClient client, MessageRequest messageRequest, Object ackSender) {
        try {
            String userId = client.get("userId");

            // 메시지 저장 및 처리
            MessageResponse savedMessage = messageService.createMessage(
                messageRequest.getRoomId(),
                messageRequest.getContent(),
                messageRequest.getFileId(),
                userId
            );

            // 룸 참여자들에게 메시지 브로드캐스트
            socketIOServer.getRoomOperations(messageRequest.getRoomId())
                    .sendEvent("new-message", savedMessage);

            // 전송자에게 성공 응답
            client.sendEvent("message-sent", savedMessage);

            log.info("메시지 전송: 사용자 {} -> 룸 {}", userId, messageRequest.getRoomId());

        } catch (Exception e) {
            log.error("메시지 전송 중 에러 발생", e);
            client.sendEvent("message-error", "메시지 전송에 실패했습니다.");
        }
    }

    /**
     * 메시지 로드 이벤트 (페이지네이션)
     */
    private void onLoadMessages(SocketIOClient client, LoadMessageRequest request, Object ackSender) {
        try {
            String userId = client.get("userId");

            // TODO: 권한 검증 (룸 참여자인지 확인)

            var messagesResponse = messageService.getMessagesWithPagination(
                request.getRoomId(),
                request.getBefore(),
                request.getLimit()
            );

            client.sendEvent("messages-loaded", messagesResponse);

        } catch (Exception e) {
            log.error("메시지 로드 중 에러 발생", e);
            client.sendEvent("load-error", "메시지 로드에 실패했습니다.");
        }
    }

    /**
     * 타이핑 시작 이벤트
     */
    private void onTypingStart(SocketIOClient client, TypingRequest request, Object ackSender) {
        try {
            String userId = client.get("userId");

            socketIOServer.getRoomOperations(request.getRoomId())
                    .sendEvent("user-typing-start", TypingResponse.builder()
                            .userId(userId)
                            .roomId(request.getRoomId())
                            .build());

        } catch (Exception e) {
            log.error("타이핑 시작 이벤트 처리 중 에러", e);
        }
    }

    /**
     * 타이핑 중지 이벤트
     */
    private void onTypingStop(SocketIOClient client, TypingRequest request, Object ackSender) {
        try {
            String userId = client.get("userId");

            socketIOServer.getRoomOperations(request.getRoomId())
                    .sendEvent("user-typing-stop", TypingResponse.builder()
                            .userId(userId)
                            .roomId(request.getRoomId())
                            .build());

        } catch (Exception e) {
            log.error("타이핑 중지 이벤트 처리 중 에러", e);
        }
    }

    /**
     * 사용자 상태 변경 이벤트
     */
    private void onUserStatusChange(SocketIOClient client, StatusRequest request, Object ackSender) {
        try {
            String userId = client.get("userId");

            // 상태 변경 브로드캐스트
            notificationService.broadcastUserStatusChange(userId, request.getStatus());

        } catch (Exception e) {
            log.error("사용자 상태 변경 중 에러", e);
        }
    }

    /**
     * 클라이언트에서 사용자 ID 추출
     */
    private String getUserIdFromClient(SocketIOClient client) {
        var authToken = (Map<?, ?>) client.getHandshakeData().getAuthToken();
        return authToken.get("token") != null ? authToken.get("token").toString() : null;
    }

    /**
     * 클라이언트에서 세션 ID 추출
     */
    private String getSessionIdFromClient(SocketIOClient client) {
        return client.getHandshakeData().getSingleUrlParam("sessionId");
    }

    // DTO classes for Socket.IO events

    public static class LoadMessageRequest {
        private String roomId;
        private String before;
        private Integer limit;

        // getters and setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getBefore() { return before; }
        public void setBefore(String before) { this.before = before; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
    }

    public static class TypingRequest {
        private String roomId;

        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
    }

    @lombok.Builder
    @lombok.Data
    public static class TypingResponse {
        private String userId;
        private String roomId;
    }

    public static class StatusRequest {
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
