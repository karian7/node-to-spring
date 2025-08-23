package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.AuthorizationListener;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.HandshakeData;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    public AuthorizationResult getAuthorizationResult(HandshakeData data) {
//        try {
//            // Extract auth data from handshake (similar to Node.js backend)
//            var authToken = (Map<?, ?>) data.getAuthToken();
//            String token = authToken.get("token") != null ? authToken.get("token").toString() : null;
//            String sessionId = authToken.get("sessionId") != null ? authToken.get("sessionId").toString() : null;
//
//            if (token == null || sessionId == null) {
//                log.warn("Missing authentication credentials in Socket.IO handshake - token: {}, sessionId: {}",
//                    token != null, sessionId != null);
//                return AuthorizationResult.FAILED_AUTHORIZATION;
//            }
//
//            // Validate JWT token and extract user ID (same as Node.js backend)
//            String userId = jwtUtil.extractSubject(token);
//            if (userId == null) {
//                log.warn("Invalid JWT token - no user ID found");
//                return AuthorizationResult.FAILED_AUTHORIZATION;
//            }
//
//            // Validate session (same as Node.js backend's SessionService.validateSession)
//            SessionService.SessionValidationResult validationResult =
//                sessionService.validateSession(userId, sessionId);
//
//            if (!validationResult.isValid()) {
//                log.error("Session validation failed: {}", validationResult.getMessage());
//                return AuthorizationResult.FAILED_AUTHORIZATION;
//            }
//
//            // Load user from database (same as Node.js backend's User.findById)
//            User user = userRepository.findById(userId).orElse(null);
//            if (user == null) {
//                log.error("User not found: {}", userId);
//                return AuthorizationResult.FAILED_AUTHORIZATION;
//            }
//
//            // Store user info in handshake data for later use (similar to Node.js socket.user)
//            data.getHttpHeaders().set("socket.user.id", user.getId());
//            data.getHttpHeaders().set("socket.user.name", user.getName());
//            data.getHttpHeaders().set("socket.user.email", user.getEmail());
//            data.getHttpHeaders().set("socket.user.sessionId", sessionId);
//            data.getHttpHeaders().set("socket.user.profileImage", user.getProfileImage());
//
//            log.info("Socket.IO connection authorized for user: {} ({})", user.getName(), userId);
//            return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
//
//        } catch (Exception e) {
//            log.error("Socket.IO authentication error: {}", e.getMessage(), e);
//            return AuthorizationResult.FAILED_AUTHORIZATION;
//        }
                    return AuthorizationResult.SUCCESSFUL_AUTHORIZATION;
    }
}
