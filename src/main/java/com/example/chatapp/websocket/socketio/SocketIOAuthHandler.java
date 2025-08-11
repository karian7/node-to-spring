package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.HandshakeData;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Socket.IO Authorization Handler
 * Mimics Node.js backend's auth logic: socket.handshake.auth.token and socket.handshake.auth.sessionId
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOAuthHandler implements AuthorizationListener {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final UserRepository userRepository;

    @Override
    public boolean isAuthorized(HandshakeData data) {
        try {
            // Extract auth data from handshake (similar to Node.js backend)
            String token = extractAuthToken(data);
            String sessionId = extractAuthSessionId(data);

            if (token == null || sessionId == null) {
                log.warn("Missing authentication credentials in Socket.IO handshake - token: {}, sessionId: {}",
                    token != null, sessionId != null);
                return false;
            }

            // Validate JWT token and extract user ID (same as Node.js backend)
            String userId = jwtUtil.extractSubject(token);
            if (userId == null) {
                log.warn("Invalid JWT token - no user ID found");
                return false;
            }

            // Validate session (same as Node.js backend's SessionService.validateSession)
            SessionService.SessionValidationResult validationResult =
                sessionService.validateSession(userId, sessionId);

            if (!validationResult.isValid()) {
                log.error("Session validation failed: {}", validationResult.getMessage());
                return false;
            }

            // Load user from database (same as Node.js backend's User.findById)
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("User not found: {}", userId);
                return false;
            }

            // Store user info in handshake data for later use (similar to Node.js socket.user)
            data.getHttpHeaders().set("socket.user.id", user.getId());
            data.getHttpHeaders().set("socket.user.name", user.getName());
            data.getHttpHeaders().set("socket.user.email", user.getEmail());
            data.getHttpHeaders().set("socket.user.sessionId", sessionId);
            data.getHttpHeaders().set("socket.user.profileImage", user.getProfileImage());

            log.info("Socket.IO connection authorized for user: {} ({})", user.getName(), userId);
            return true;

        } catch (Exception e) {
            log.error("Socket.IO authentication error: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extract token from auth object (similar to Node.js: socket.handshake.auth.token)
     */
    private String extractAuthToken(HandshakeData data) {
        // Try to get from query parameters first (auth.token)
        List<String> tokenParams = data.getUrlParams().get("token");
        if (tokenParams != null && !tokenParams.isEmpty()) {
            return tokenParams.get(0);
        }

        // Try to get from HTTP headers as fallback
        String authHeader = data.getHttpHeaders().get("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }

    /**
     * Extract sessionId from auth object (similar to Node.js: socket.handshake.auth.sessionId)
     */
    private String extractAuthSessionId(HandshakeData data) {
        // Try to get from query parameters first (auth.sessionId)
        List<String> sessionParams = data.getUrlParams().get("sessionId");
        if (sessionParams != null && !sessionParams.isEmpty()) {
            return sessionParams.get(0);
        }

        // Try to get from HTTP headers as fallback
        String sessionHeader = data.getHttpHeaders().get("X-Session-Id");
        if (sessionHeader != null) {
            return sessionHeader;
        }

        return null;
    }
}
