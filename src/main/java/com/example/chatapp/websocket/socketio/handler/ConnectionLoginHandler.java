package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.util.JwtUtil;
import com.example.chatapp.websocket.socketio.UserSessionRegistry;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * Socket.IO Chat Handler
 * 어노테이션 기반 이벤트 처리와 인증 흐름을 정의한다.
 * 연결/해제 및 중복 로그인 처리를 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionLoginHandler {

    private final SocketIOServer socketIOServer;
    private final JwtUtil jwtUtil;
    private final UserSessionRegistry socketSessionRegistry;
    private final PendingDuplicateLoginManager pendingLoginManager;
    
    /**
     * auth 처리가 선행되어야 해서 @OnConnect 대신 별도 메서드로 구현
     */
    public void onConnect(SocketIOClient client) {
        String userId = getUserId(client);
        if (userId == null) {
            return;
        }
        
        try {
            String newSocketId = client.getSessionId().toString();
            String existingSocketId = socketSessionRegistry.getSocketIdForUser(userId);
            if (existingSocketId != null) {
                // 다중노드 지원안됨.
                SocketIOClient existingClient = socketIOServer.getClient(UUID.fromString(existingSocketId));
                if (existingClient != null) {
                    // Send duplicate login notification
                    existingClient.sendEvent(DUPLICATE_LOGIN, Map.of(
                            "type", "new_login_attempt",
                            "deviceInfo", client.getHandshakeData().getHttpHeaders().get("User-Agent"),
                            "ipAddress", client.getRemoteAddress().toString(),
                            "timestamp", System.currentTimeMillis()
                    ));
                    
                    // 중복 로그인 대기 상태 등록
                    pendingLoginManager.registerPending(
                        userId,
                        existingSocketId,
                        client,
                        newSocketId,
                        () -> {
                            existingClient.sendEvent(SESSION_ENDED, Map.of(
                                "reason", "duplicate_login",
                                "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
                            ));
                            existingClient.disconnect();
                        }
                    );
                }
            }
            
            // Store new connection in Redis
            socketSessionRegistry.saveSocketIdForUser(userId, newSocketId);
            
            log.info("Socket.IO user connected: {} ({})", getUserName(client), userId);
            
            // Join user to their personal room for direct messages
            client.joinRoom("user:" + userId);
            
        } catch (Exception e) {
            log.error("Error handling Socket.IO connection", e);
            client.sendEvent(ERROR, Map.of(
                    "message", "연결 처리 중 오류가 발생했습니다."
            ));
        }
    }
    
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String userId = getUserId(client);
        if (userId == null) {
            return;
        }
        try {
            String socketId = client.getSessionId().toString();
            String userName = getUserName(client);
            String mappedSocketId = socketSessionRegistry.getSocketIdForUser(userId);

            // Redis에서 매핑된 socketId와 일치하면 제거
            if (socketId.equals(mappedSocketId)) {
                socketSessionRegistry.removeSocketIdForUser(userId);
            }

            // 중복 로그인 처리 확인
            if (pendingLoginManager.involvesSocketId(userId, socketId)) {
                pendingLoginManager.handleDisconnectWithRestore(
                    userId,
                    socketId,
                    sid -> socketIOServer.getClient(UUID.fromString(sid)),
                    existingSocketId -> socketSessionRegistry.saveSocketIdForUser(userId, existingSocketId)
                );
            }
            
            log.info("Socket.IO user disconnected: {} ({})", userName, userId);
        } catch (Exception e) {
            log.error("Error handling Socket.IO disconnection", e);
            client.sendEvent(ERROR, Map.of(
                "message", "연결 종료 처리 중 오류가 발생했습니다."
            ));
        }
    }

    @OnEvent(FORCE_LOGIN)
    public void onForceLogin(SocketIOClient client, Map<String, Object> data) {
        try {
            String userId = getUserId(client);
            if (userId == null) return;

            String token = data != null ? (String) data.get("token") : null;
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

            // Pending 상태 제거
            pendingLoginManager.handleForceLogin(userId);

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
    }

    @OnEvent(KEEP_EXISTING_SESSION)
    public void onKeepExistingSession(SocketIOClient client, Map<String, Object> data) {
        try {
            String userId = getUserId(client);
            if (userId == null) return;

            String token = data != null ? (String) data.get("token") : null;
            
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

            // 기존 세션 유지 처리
            boolean handled = pendingLoginManager.handleKeepExistingSession(
                userId,
                newClient -> {
                    newClient.sendEvent(SESSION_ENDED, Map.of(
                        "reason", "keep_existing",
                        "message", "기존 세션을 유지합니다."
                    ));
                    newClient.disconnect();
                },
                existingSocketId -> socketSessionRegistry.saveSocketIdForUser(userId, existingSocketId)
            );
            
            if (handled) {
                log.info("Keep existing session acknowledged - userId: {}", userId);
            }

        } catch (Exception e) {
            log.error("Keep existing session error", e);
            client.sendEvent(ERROR, Map.of(
                "message", "세션 처리 중 오류가 발생했습니다."
            ));
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
