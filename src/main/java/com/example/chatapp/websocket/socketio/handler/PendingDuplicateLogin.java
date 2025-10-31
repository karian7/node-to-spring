package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import java.util.concurrent.ScheduledFuture;

/**
 * 중복 로그인 정보를 담는 내부 클래스
 */
public class PendingDuplicateLogin {
    private final String existingSocketId;
    private final SocketIOClient newClient;
    private final String newSocketId;
    private ScheduledFuture<?> timeoutTask;
    
    PendingDuplicateLogin(String existingSocketId,
                          SocketIOClient newClient,
                          String newSocketId) {
        this.existingSocketId = existingSocketId;
        this.newClient = newClient;
        this.newSocketId = newSocketId;
    }
    
    void setTimeoutTask(ScheduledFuture<?> timeoutTask) {
        this.timeoutTask = timeoutTask;
    }
    
    void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(true);
        }
    }
    
    boolean involves(String socketId) {
        return socketId != null
                && (socketId.equals(existingSocketId) || socketId.equals(newSocketId));
    }
    
    String getExistingSocketId() {
        return existingSocketId;
    }
    
    String getNewSocketId() {
        return newSocketId;
    }
    
    SocketIOClient getNewClient() {
        return newClient;
    }
}
