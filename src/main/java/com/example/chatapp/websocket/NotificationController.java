package com.example.chatapp.websocket;

import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;

@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final SimpMessageSendingOperations messagingTemplate;
    private final UserRepository userRepository;
    private final WebSocketEventListener webSocketEventListener;

    // 타이핑 상태 브로드캐스트
    @MessageMapping("/typing.start")
    public void handleTypingStart(@Payload TypingRequest typingRequest, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            TypingEvent typingEvent = new TypingEvent(
                    user.getId(),
                    user.getName(),
                    typingRequest.roomId(),
                    true,
                    LocalDateTime.now()
            );

            messagingTemplate.convertAndSend("/topic/rooms/" + typingRequest.roomId() + "/typing", typingEvent);
            log.debug("User {} started typing in room {}", user.getName(), typingRequest.roomId());

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
                    typingRequest.roomId(),
                    false,
                    LocalDateTime.now()
            );

            messagingTemplate.convertAndSend("/topic/rooms/" + typingRequest.roomId() + "/typing", typingEvent);
            log.debug("User {} stopped typing in room {}", user.getName(), typingRequest.roomId());

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

    // DTO record 클래스들
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
}
