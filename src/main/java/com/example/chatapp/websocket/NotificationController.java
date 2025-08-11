package com.example.chatapp.websocket;

import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Slf4j
@Controller
public class NotificationController {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WebSocketEventListener webSocketEventListener;

    // 타이핑 상태 브로드캐스트
    @MessageMapping("/typing.start")
    public void handleTypingStart(@Payload TypingRequest typingRequest, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            TypingEvent typingEvent = new TypingEvent(
                    user.getId(),
                    user.getName(),
                    typingRequest.getRoomId(),
                    true,
                    LocalDateTime.now()
            );

            messagingTemplate.convertAndSend("/topic/rooms/" + typingRequest.getRoomId() + "/typing", typingEvent);
            log.debug("User {} started typing in room {}", user.getName(), typingRequest.getRoomId());

        } catch (Exception e) {
            log.error("Error handling typing start", e);
        }
    }

    @MessageMapping("/typing.stop")
    public void handleTypingStop(@Payload TypingRequest typingRequest, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            TypingEvent typingEvent = new TypingEvent(
                    user.getId(),
                    user.getName(),
                    typingRequest.getRoomId(),
                    false,
                    LocalDateTime.now()
            );

            messagingTemplate.convertAndSend("/topic/rooms/" + typingRequest.getRoomId() + "/typing", typingEvent);
            log.debug("User {} stopped typing in room {}", user.getName(), typingRequest.getRoomId());

        } catch (Exception e) {
            log.error("Error handling typing stop", e);
        }
    }

    // 온라인 사용자 상태 조회
    @MessageMapping("/users.online")
    public void getOnlineUsers(Principal principal) {
        try {
            int onlineCount = webSocketEventListener.getOnlineUserCount();
            OnlineUsersResponse response = new OnlineUsersResponse(onlineCount, LocalDateTime.now());

            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/topic/users.online",
                    response
            );

        } catch (Exception e) {
            log.error("Error getting online users", e);
        }
    }

    // 사용자 활동 상태 업데이트
    @MessageMapping("/user.activity")
    public void updateUserActivity(Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            user.setLastActive(LocalDateTime.now());
            userRepository.save(user);

            log.debug("Updated activity for user: {}", user.getName());

        } catch (Exception e) {
            log.error("Error updating user activity", e);
        }
    }

    // DTO 클래스들
    public static class TypingRequest {
        private String roomId;

        public TypingRequest() {}

        public TypingRequest(String roomId) {
            this.roomId = roomId;
        }

        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
    }

    public static class TypingEvent {
        private String userId;
        private String userName;
        private String roomId;
        private boolean isTyping;
        private LocalDateTime timestamp;

        public TypingEvent(String userId, String userName, String roomId, boolean isTyping, LocalDateTime timestamp) {
            this.userId = userId;
            this.userName = userName;
            this.roomId = roomId;
            this.isTyping = isTyping;
            this.timestamp = timestamp;
        }

        // Getters
        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getRoomId() { return roomId; }
        public boolean isTyping() { return isTyping; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class OnlineUsersResponse {
        private int count;
        private LocalDateTime timestamp;

        public OnlineUsersResponse(int count, LocalDateTime timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }

        // Getters
        public int getCount() { return count; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}
