package com.example.chatapp.service;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Socket.IO Server Service
 * Manages the lifecycle of Socket.IO server to provide Node.js compatible WebSocket API
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("socketio") // Only active when socketio profile is enabled
public class SocketIOService {

    private final SocketIOServer socketIOServer;

    @EventListener(ApplicationReadyEvent.class)
    public void startSocketIOServer() {
        try {
            socketIOServer.start();
            log.info("Socket.IO server started successfully on port: {}",
                socketIOServer.getConfiguration().getPort());
            log.info("Socket.IO server is now compatible with Node.js backend auth: { token, sessionId }");
        } catch (Exception e) {
            log.error("Failed to start Socket.IO server", e);
            throw new RuntimeException("Socket.IO server startup failed", e);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void stopSocketIOServer() {
        try {
            if (socketIOServer != null) {
                socketIOServer.stop();
                log.info("Socket.IO server stopped successfully");
            }
        } catch (Exception e) {
            log.error("Error stopping Socket.IO server", e);
        }
    }

    public boolean isServerRunning() {
        return socketIOServer != null && socketIOServer.getConfiguration() != null;
    }

    public int getConnectedClientsCount() {
        return socketIOServer.getAllClients().size();
    }
}
