package com.example.chatapp.service;

import com.corundumstudio.socketio.SocketIOClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Socket.IO 연결 상태 관리 고도화 서비스
 * Node.js backend의 연결 관리와 동일한 기능 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionStateManager {

    private final RedisTemplate<String, Object> redisTemplate;

    // 메모리 기반 연결 상태 (빠른 접근용)
    private final ConcurrentHashMap<String, UserConnectionInfo> connectedUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> roomParticipants = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userCurrentRoom = new ConcurrentHashMap<>();

    // Redis 키 상수
    private static final String REDIS_USER_STATUS_KEY = "chat:user:status:";
    private static final String REDIS_ROOM_USERS_KEY = "chat:room:users:";
    private static final String REDIS_USER_SESSION_KEY = "chat:user:session:";

    /**
     * 사용자 연결 등록
     */
    public void registerUserConnection(String userId, SocketIOClient client, String sessionId) {
        try {
            UserConnectionInfo connectionInfo = UserConnectionInfo.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .client(client)
                    .connectedAt(LocalDateTime.now())
                    .lastActivity(LocalDateTime.now())
                    .status("online")
                    .build();

            // 메모리에 저장
            connectedUsers.put(userId, connectionInfo);

            // Redis에 사용자 상태 저장 (TTL 설정)
            redisTemplate.opsForValue().set(
                REDIS_USER_STATUS_KEY + userId,
                "online",
                30,
                TimeUnit.MINUTES
            );

            // 세션 정보 Redis에 저장
            redisTemplate.opsForHash().putAll(
                REDIS_USER_SESSION_KEY + userId,
                Map.of(
                    "sessionId", sessionId,
                    "connectedAt", connectionInfo.getConnectedAt().toString(),
                    "status", "online"
                )
            );

            log.info("사용자 연결 등록: {} (세션: {})", userId, sessionId);

        } catch (Exception e) {
            log.error("사용자 연결 등록 중 에러: {}", userId, e);
        }
    }

    /**
     * 사용자 연결 해제
     */
    public void unregisterUserConnection(String userId) {
        try {
            UserConnectionInfo connectionInfo = connectedUsers.remove(userId);
            if (connectionInfo != null) {
                // 현재 룸에서 제거
                String currentRoom = userCurrentRoom.remove(userId);
                if (currentRoom != null) {
                    removeUserFromRoom(userId, currentRoom);
                }

                // Redis에서 상태 업데이트
                redisTemplate.opsForValue().set(
                    REDIS_USER_STATUS_KEY + userId,
                    "offline",
                    24,
                    TimeUnit.HOURS
                );

                // 세션 정보 업데이트
                redisTemplate.opsForHash().put(
                    REDIS_USER_SESSION_KEY + userId,
                    "status",
                    "offline"
                );
                redisTemplate.opsForHash().put(
                    REDIS_USER_SESSION_KEY + userId,
                    "disconnectedAt",
                    LocalDateTime.now().toString()
                );

                log.info("사용자 연결 해제: {}", userId);
            }

        } catch (Exception e) {
            log.error("사용자 연결 해제 중 에러: {}", userId, e);
        }
    }

    /**
     * 사용자 룸 입장 등록
     */
    public void addUserToRoom(String userId, String roomId) {
        try {
            // 이전 룸에서 제거
            String previousRoom = userCurrentRoom.get(userId);
            if (previousRoom != null && !previousRoom.equals(roomId)) {
                removeUserFromRoom(userId, previousRoom);
            }

            // 새 룸에 추가
            roomParticipants.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(userId);
            userCurrentRoom.put(userId, roomId);

            // 활동 시간 업데이트
            updateUserActivity(userId);

            // Redis에 룸 참여자 정보 저장
            redisTemplate.opsForSet().add(REDIS_ROOM_USERS_KEY + roomId, userId);

            log.debug("사용자 {} 룸 {} 입장 등록", userId, roomId);

        } catch (Exception e) {
            log.error("사용자 룸 입장 등록 중 에러: {} -> {}", userId, roomId, e);
        }
    }

    /**
     * 사용자 룸 나가기
     */
    public void removeUserFromRoom(String userId, String roomId) {
        try {
            Set<String> participants = roomParticipants.get(roomId);
            if (participants != null) {
                participants.remove(userId);
                if (participants.isEmpty()) {
                    roomParticipants.remove(roomId);
                }
            }

            userCurrentRoom.remove(userId);

            // Redis에서 제거
            redisTemplate.opsForSet().remove(REDIS_ROOM_USERS_KEY + roomId, userId);

            log.debug("사용자 {} 룸 {} 나가기", userId, roomId);

        } catch (Exception e) {
            log.error("사용자 룸 나가기 중 에러: {} -> {}", userId, roomId, e);
        }
    }

    /**
     * 사용자 활동 시간 업데이트
     */
    public void updateUserActivity(String userId) {
        UserConnectionInfo connectionInfo = connectedUsers.get(userId);
        if (connectionInfo != null) {
            connectionInfo.setLastActivity(LocalDateTime.now());

            // Redis TTL 갱신
            redisTemplate.expire(REDIS_USER_STATUS_KEY + userId, 30, TimeUnit.MINUTES);
        }
    }

    /**
     * 사용자 상태 변경
     */
    public void updateUserStatus(String userId, String status) {
        try {
            UserConnectionInfo connectionInfo = connectedUsers.get(userId);
            if (connectionInfo != null) {
                connectionInfo.setStatus(status);
                connectionInfo.setLastActivity(LocalDateTime.now());

                // Redis 업데이트
                redisTemplate.opsForValue().set(
                    REDIS_USER_STATUS_KEY + userId,
                    status,
                    30,
                    TimeUnit.MINUTES
                );

                redisTemplate.opsForHash().put(
                    REDIS_USER_SESSION_KEY + userId,
                    "status",
                    status
                );
            }

        } catch (Exception e) {
            log.error("사용자 상태 업데이트 중 에러: {}", userId, e);
        }
    }

    /**
     * 연결된 사용자 확인
     */
    public boolean isUserConnected(String userId) {
        return connectedUsers.containsKey(userId);
    }

    /**
     * 사용자의 현재 룸 조회
     */
    public String getUserCurrentRoom(String userId) {
        return userCurrentRoom.get(userId);
    }

    /**
     * 룸의 참여자 목록 조회
     */
    public Set<String> getRoomParticipants(String roomId) {
        return roomParticipants.getOrDefault(roomId, Collections.emptySet());
    }

    /**
     * 연결된 사용자 수 조회
     */
    public int getConnectedUserCount() {
        return connectedUsers.size();
    }

    /**
     * 활성 룸 수 조회
     */
    public int getActiveRoomCount() {
        return roomParticipants.size();
    }

    /**
     * 사용자 연결 정보 조회
     */
    public Optional<UserConnectionInfo> getUserConnectionInfo(String userId) {
        return Optional.ofNullable(connectedUsers.get(userId));
    }

    /**
     * Redis에서 사용자 상태 조회 (다른 서버 인스턴스의 연결 포함)
     */
    public String getUserStatusFromRedis(String userId) {
        try {
            Object status = redisTemplate.opsForValue().get(REDIS_USER_STATUS_KEY + userId);
            return status != null ? status.toString() : "offline";
        } catch (Exception e) {
            log.error("Redis에서 사용자 상태 조회 중 에러: {}", userId, e);
            return "offline";
        }
    }

    /**
     * 비활성 연결 정리 (주기적 실행용)
     */
    public void cleanupInactiveConnections() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);

        connectedUsers.entrySet().removeIf(entry -> {
            UserConnectionInfo info = entry.getValue();
            if (info.getLastActivity().isBefore(cutoffTime)) {
                String userId = entry.getKey();
                log.info("비활성 연결 정리: {}", userId);

                // 룸에서 제거
                String currentRoom = userCurrentRoom.get(userId);
                if (currentRoom != null) {
                    removeUserFromRoom(userId, currentRoom);
                }

                // Redis 상태 업데이트
                redisTemplate.opsForValue().set(
                    REDIS_USER_STATUS_KEY + userId,
                    "offline",
                    24,
                    TimeUnit.HOURS
                );

                return true;
            }
            return false;
        });
    }

    /**
     * 연결 통계 정보 조회
     */
    public ConnectionStats getConnectionStats() {
        return ConnectionStats.builder()
                .totalConnectedUsers(connectedUsers.size())
                .totalActiveRooms(roomParticipants.size())
                .averageUsersPerRoom(roomParticipants.values().stream()
                        .mapToInt(Set::size)
                        .average()
                        .orElse(0.0))
                .timestamp(LocalDateTime.now())
                .build();
    }

    // 연결 정보 DTO
    @lombok.Builder
    @lombok.Data
    public static class UserConnectionInfo {
        private String userId;
        private String sessionId;
        private SocketIOClient client;
        private LocalDateTime connectedAt;
        private LocalDateTime lastActivity;
        private String status;
    }

    // 연결 통계 DTO
    @lombok.Builder
    @lombok.Data
    public static class ConnectionStats {
        private int totalConnectedUsers;
        private int totalActiveRooms;
        private double averageUsersPerRoom;
        private LocalDateTime timestamp;
    }
}
