package com.example.chatapp.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.chatapp.websocket.socketio.SocketIOAuthHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOConfig {

    private final SocketIOAuthHandler authHandler;

    @Value("${socketio.server.host:localhost}")
    private String host;

    @Value("${socketio.server.port:9092}")
    private Integer port;

    @Bean
    public SocketIOServer socketIOServer() {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);

        // CORS settings
        config.setOrigin("*");

        // Socket.IO settings (same as Node.js backend)
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        config.setUpgradeTimeout(10000);

        // Enable auth support with our custom handler
        // This mimics Node.js: socket.handshake.auth.token and socket.handshake.auth.sessionId
        config.setAuthorizationListener(authHandler);

        log.info("Socket.IO server configured on {}:{} with Node.js compatible auth", host, port);
        return new SocketIOServer(config);
    }
}
