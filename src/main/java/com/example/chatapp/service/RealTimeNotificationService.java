package com.example.chatapp.service;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.chatapp.dto.NotificationDto;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 실시간 알림 시스템 서비스
 * Node.js backend의 알림 시스템과 동일한 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeNotificationService {

    private final SocketIOServer socketIOServer;
    private final UserRepository userRepository;

    /**
     * 특정 사용자에게 실시간 알림 전송
     */
    public void sendNotificationToUser(String userId, String type, Object data) {
        try {
            NotificationDto notification = NotificationDto.builder()
                    .type(type)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 해당 사용자의 모든 연결된 클라이언트에게 알림 전송
            socketIOServer.getBroadcastOperations()
                    .sendEvent("notification:" + userId, notification);

            log.debug("알림 전송 완료: 사용자 {} - 타입 {}", userId, type);

        } catch (Exception e) {
            log.error("사용자별 알림 전송 중 에러 발생: {}", userId, e);
        }
    }

    /**
     * 여러 사용자에게 실시간 알림 전송
     */
    public void sendNotificationToUsers(List<String> userIds, String type, Object data) {
        CompletableFuture.runAsync(() -> {
            userIds.parallelStream().forEach(userId -> {
                sendNotificationToUser(userId, type, data);
            });
        });
    }

    /**
     * 룸의 모든 참여자에게 실시간 알림 전송
     */
    public void sendNotificationToRoom(String roomId, String type, Object data, String excludeUserId) {
        try {
            NotificationDto notification = NotificationDto.builder()
                    .type(type)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build();

            if (excludeUserId != null) {
                // 특정 사용자 제외하고 룸에 알림
                socketIOServer.getRoomOperations(roomId)
                        .sendEvent("room-notification", notification);
                // TODO: 제외 로직 구현 필요
            } else {
                // 룸 전체에 알림
                socketIOServer.getRoomOperations(roomId)
                        .sendEvent("room-notification", notification);
            }

            log.debug("룸 알림 전송 완료: 룸 {} - 타입 {}", roomId, type);

        } catch (Exception e) {
            log.error("룸 알림 전송 중 에러 발생: {}", roomId, e);
        }
    }

    /**
     * 전체 사용자에게 브로드캐스트 알림 전송
     */
    public void broadcastNotification(String type, Object data) {
        try {
            NotificationDto notification = NotificationDto.builder()
                    .type(type)
                    .data(data)
                    .timestamp(LocalDateTime.now())
                    .build();

            socketIOServer.getBroadcastOperations()
                    .sendEvent("broadcast-notification", notification);

            log.info("브로드캐스트 알림 전송 완료: 타입 {}", type);

        } catch (Exception e) {
            log.error("브로드캐스트 알림 전송 중 에러 발생", e);
        }
    }

    /**
     * 사용자 상태 변경 브로드캐스트
     */
    public void broadcastUserStatusChange(String userId, String status) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.warn("사용자 상태 변경 브로드캐스트 실패: 사용자를 찾을 수 없음 {}", userId);
                return;
            }

            UserStatusChangeDto statusChange = UserStatusChangeDto.builder()
                    .userId(userId)
                    .userName(user.getName())
                    .status(status)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 전체 사용자에게 상태 변경 알림
            socketIOServer.getBroadcastOperations()
                    .sendEvent("user-status-changed", statusChange);

            log.debug("사용자 상태 변경 브로드캐스트: {} - {}", userId, status);

        } catch (Exception e) {
            log.error("사용자 상태 변경 브로드캐스트 중 에러: {}", userId, e);
        }
    }

    /**
     * 새 메시지 알림
     */
    public void notifyNewMessage(String roomId, String senderId, String messageContent) {
        try {
            NewMessageNotificationDto notification = NewMessageNotificationDto.builder()
                    .roomId(roomId)
                    .senderId(senderId)
                    .messagePreview(truncateMessage(messageContent))
                    .timestamp(LocalDateTime.now())
                    .build();

            sendNotificationToRoom(roomId, "new-message", notification, senderId);

        } catch (Exception e) {
            log.error("새 메시지 알림 전송 중 에러: 룸 {}", roomId, e);
        }
    }

    /**
     * 파일 업로드 완료 알림
     */
    public void notifyFileUpload(String roomId, String uploaderId, String fileName) {
        try {
            FileUploadNotificationDto notification = FileUploadNotificationDto.builder()
                    .roomId(roomId)
                    .uploaderId(uploaderId)
                    .fileName(fileName)
                    .timestamp(LocalDateTime.now())
                    .build();

            sendNotificationToRoom(roomId, "file-uploaded", notification, uploaderId);

        } catch (Exception e) {
            log.error("파일 업로드 알림 전송 중 에러: 룸 {}", roomId, e);
        }
    }

    /**
     * 룸 생성/수정 알림
     */
    public void notifyRoomCreated(String roomId, String creatorId, String roomName) {
        try {
            RoomEventNotificationDto notification = RoomEventNotificationDto.builder()
                    .roomId(roomId)
                    .creatorId(creatorId)
                    .roomName(roomName)
                    .eventType("created")
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcastNotification("room-created", notification);

        } catch (Exception e) {
            log.error("룸 생성 알림 전송 중 에러: 룸 {}", roomId, e);
        }
    }

    /**
     * 사용자 룸 참여/나가기 알림
     */
    public void notifyUserJoinedRoom(String roomId, String userId, String action) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            UserRoomEventDto notification = UserRoomEventDto.builder()
                    .roomId(roomId)
                    .userId(userId)
                    .userName(user.getName())
                    .action(action) // "joined" or "left"
                    .timestamp(LocalDateTime.now())
                    .build();

            sendNotificationToRoom(roomId, "user-room-event", notification, null);

        } catch (Exception e) {
            log.error("사용자 룸 이벤트 알림 전송 중 에러: 룸 {} - 사용자 {}", roomId, userId, e);
        }
    }

    /**
     * 시스템 알림 (점검, 업데이트 등)
     */
    public void sendSystemNotification(String title, String message, String priority) {
        try {
            SystemNotificationDto notification = SystemNotificationDto.builder()
                    .title(title)
                    .message(message)
                    .priority(priority) // "low", "medium", "high", "critical"
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcastNotification("system-notification", notification);

            log.info("시스템 알림 전송: {} - {}", title, priority);

        } catch (Exception e) {
            log.error("시스템 알림 전송 중 에러", e);
        }
    }

    /**
     * 메시지 내용 미리보기용 자르기
     */
    private String truncateMessage(String message) {
        if (message == null) return "";
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }

    // DTO classes for various notification types

    @lombok.Builder
    @lombok.Data
    public static class UserStatusChangeDto {
        private String userId;
        private String userName;
        private String status;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class NewMessageNotificationDto {
        private String roomId;
        private String senderId;
        private String messagePreview;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class FileUploadNotificationDto {
        private String roomId;
        private String uploaderId;
        private String fileName;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class RoomEventNotificationDto {
        private String roomId;
        private String creatorId;
        private String roomName;
        private String eventType;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class UserRoomEventDto {
        private String roomId;
        private String userId;
        private String userName;
        private String action;
        private LocalDateTime timestamp;
    }

    @lombok.Builder
    @lombok.Data
    public static class SystemNotificationDto {
        private String title;
        private String message;
        private String priority;
        private LocalDateTime timestamp;
    }
}
