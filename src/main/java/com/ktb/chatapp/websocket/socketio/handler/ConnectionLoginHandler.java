package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.ktb.chatapp.websocket.socketio.ConnectedUsers;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * Socket.IO Chat Handler
 * 어노테이션 기반 이벤트 처리와 인증 흐름을 정의한다.
 * 연결/해제 및 중복 로그인 처리를 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ConnectionLoginHandler {

    private final SocketIOServer socketIOServer;
    private final ConnectedUsers connectedUsers;
    private final RoomLeaveHandler roomLeaveHandler;
    private final UserRooms userRooms;
    private final TaskScheduler taskScheduler;
    
    /**
     * auth 처리가 선행되어야 해서 @OnConnect 대신 별도 메서드로 구현
     */
    public void onConnect(SocketIOClient client, SocketUser user) {
        String userId = user.id();
        
        try {
            var socketUser = connectedUsers.get(userId);
            if (socketUser != null) {
                // 다중노드 지원안됨.
                String existingSocketId = socketUser.socketId();
                SocketIOClient existingClient = socketIOServer.getClient(UUID.fromString(existingSocketId));
                if (existingClient != null) {
                    // Send duplicate login notification
                    existingClient.sendEvent(DUPLICATE_LOGIN, Map.of(
                            "type", "new_login_attempt",
                            "deviceInfo", client.getHandshakeData().getHttpHeaders().get("User-Agent"),
                            "ipAddress", client.getRemoteAddress().toString(),
                            "timestamp", System.currentTimeMillis()
                    ));
                    
                    taskScheduler.schedule(() -> {
                        existingClient.sendEvent(SESSION_ENDED, Map.of(
                                "reason", "duplicate_login",
                                "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
                        ));
                        existingClient.disconnect();
                    }, taskScheduler.getClock().instant().plusSeconds(10));
                }
            }
            
            connectedUsers.set(userId, user);
            client.set("user", user);
            
            log.info("Socket.IO user connected: {} ({})", getUserName(client), userId);
            
            client.joinRooms(Set.of("user:" + userId, "room-list"));
            
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
            
            // 해당 사용자의 현재 활성 연결인 경우에만 정리
            var socketUser = connectedUsers.get(userId);
            if (socketUser != null && socketId.equals(socketUser.socketId())) {
                connectedUsers.del(userId);
            }
            var roomId = userRooms.get(userId);
            if (roomId != null) {
                roomLeaveHandler.handleLeaveRoom(client, roomId);
            }
            client.leaveRooms(Set.of("user:" + userId, "room-list"));
            client.del("user");
            
            log.info("Socket.IO user disconnected: {} ({})", getUserName(client), userId);
        } catch (Exception e) {
            log.error("Error handling Socket.IO disconnection", e);
            client.sendEvent(ERROR, Map.of(
                "message", "연결 종료 처리 중 오류가 발생했습니다."
            ));
        }
    }

    private SocketUser getUserDto(SocketIOClient client) {
        return client.get("user");
    }
    
    private String getUserId(SocketIOClient client) {
        SocketUser user = getUserDto(client);
        return user != null ? user.id() : null;
    }
    
    private String getUserName(SocketIOClient client) {
        SocketUser user = getUserDto(client);
        return user != null ? user.name() : null;
    }
}
