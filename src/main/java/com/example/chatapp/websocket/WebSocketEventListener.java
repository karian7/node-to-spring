package com.example.chatapp.websocket;

import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketEventListener {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    // 온라인 사용자 세션 관리
    private final ConcurrentHashMap<String, String> onlineUsers = new ConcurrentHashMap<>(); // sessionId -> userId
    private final ConcurrentHashMap<String, String> userSessions = new ConcurrentHashMap<>(); // userId -> sessionId

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        Authentication authentication = (Authentication) headerAccessor.getUser();
        if (authentication != null) {
            String username = authentication.getName();

            try {
                User user = userRepository.findByEmail(username).orElse(null);
                if (user != null) {
                    // 사용자 온라인 상태 업데이트
                    user.setLastActive(LocalDateTime.now());
                    userRepository.save(user);

                    // 온라인 사용자 목록에 추가
                    onlineUsers.put(sessionId, user.getId());
                    userSessions.put(user.getId(), sessionId);

                    log.info("User connected: {} (Session: {})", user.getName(), sessionId);

                    // 온라인 상태 브로드캐스트
                    broadcastUserOnlineStatus(user.getId(), true);
                }
            } catch (Exception e) {
                log.error("Error handling WebSocket connection for user: {}", username, e);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        String userId = onlineUsers.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId);

            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user != null) {
                    // 사용자 마지막 활동 시간 업데이트
                    user.setLastActive(LocalDateTime.now());
                    userRepository.save(user);

                    log.info("User disconnected: {} (Session: {})", user.getName(), sessionId);

                    // 오프라인 상태 브로드캐스트
                    broadcastUserOnlineStatus(userId, false);
                }
            } catch (Exception e) {
                log.error("Error handling WebSocket disconnection for user: {}", userId, e);
            }
        }
    }

    private void broadcastUserOnlineStatus(String userId, boolean isOnline) {
        try {
            UserOnlineStatusEvent statusEvent = new UserOnlineStatusEvent(userId, isOnline, LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/user.status", statusEvent);
        } catch (Exception e) {
            log.error("Error broadcasting user online status", e);
        }
    }

    // 온라인 사용자 목록 조회
    public boolean isUserOnline(String userId) {
        return userSessions.containsKey(userId);
    }

    // 특정 사용자에게 메시지 전송
    public void sendToUser(String userId, String destination, Object message) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null) {
            messagingTemplate.convertAndSendToUser(sessionId, destination, message);
        }
    }

    // 온라인 사용자 수 조회
    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    // 사용자 온라인 상태 이벤트 DTO
    public static class UserOnlineStatusEvent {
        private String userId;
        private boolean isOnline;
        private LocalDateTime timestamp;

        public UserOnlineStatusEvent(String userId, boolean isOnline, LocalDateTime timestamp) {
            this.userId = userId;
            this.isOnline = isOnline;
            this.timestamp = timestamp;
        }

        // Getters
        public String getUserId() {
            return userId;
        }

        public boolean isOnline() {
            return isOnline;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}
