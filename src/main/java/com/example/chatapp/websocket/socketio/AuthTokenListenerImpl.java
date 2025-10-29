package com.example.chatapp.websocket.socketio;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.AuthTokenResult;
import com.corundumstudio.socketio.SocketIOClient;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Socket.IO Authorization Handler
 * socket.handshake.auth.token과 sessionId를 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthTokenListenerImpl implements AuthTokenListener {

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final ObjectProvider<SocketIOChatHandler> socketIOChatHandlerProvider;
    

    @Override
    public AuthTokenResult getAuthTokenResult(Object _authToken, SocketIOClient client) {
        try {
            var authToken = (Map<?, ?>) _authToken;
            String token = authToken.get("token") != null ? authToken.get("token").toString() : null;
            String sessionId = authToken.get("sessionId") != null ? authToken.get("sessionId").toString() : null;

            if (token == null || sessionId == null) {
                log.warn("Missing authentication credentials in Socket.IO handshake - token: {}, sessionId: {}",
                        token != null, sessionId != null);
                return new AuthTokenResult(false, "Authentication error");
            }

            // Validate JWT token and extract user ID
            String userId = jwtUtil.extractSubject(token);
            if (userId == null) {
                log.warn("Invalid JWT token - no user ID found");
                return new AuthTokenResult(false, "Invalid token");
            }

            // Validate session using SessionService
            SessionService.SessionValidationResult validationResult =
                    sessionService.validateSession(userId, sessionId);

            if (!validationResult.isValid()) {
                log.error("Session validation failed: {}", validationResult.getMessage());
                return new AuthTokenResult(false, "Invalid session");
            }

            // Load user from database
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                log.error("User not found: {}", userId);
                return new AuthTokenResult(false, "User not found");
            }
            // Store user info as a single object in client attributes
            UserResponse userResponse = UserResponse.from(user);
            
            client.set("user", userResponse);
            client.set("sessionId", sessionId);

            log.info("Socket.IO connection authorized for user: {} ({})", user.getName(), userId);
            socketIOChatHandlerProvider.getObject().onConnect(client);
            return AuthTokenResult.AuthTokenResultSuccess;
        } catch (Exception e) {
            log.error("Socket.IO authentication error: {}", e.getMessage(), e);
            throw new RuntimeException("Unauthorized: " + e.getMessage(), e);
        }
    }
}
