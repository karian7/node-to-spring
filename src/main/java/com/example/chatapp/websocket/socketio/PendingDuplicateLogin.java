package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import java.util.concurrent.ScheduledFuture;

final class PendingDuplicateLogin {
    static final int DUPLICATE_LOGIN_TIMEOUT = 10000;
    private final SocketIOClient existingClient;
    private final String existingSocketId;
    private final SocketIOClient newClient;
    private final String newSocketId;
    private ScheduledFuture<?> timeoutTask;
    
    PendingDuplicateLogin(SocketIOClient existingClient, String existingSocketId,
                          SocketIOClient newClient, String newSocketId) {
        this.existingClient = existingClient;
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
