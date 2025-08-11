package com.example.chatapp.websocket.interceptor;

import com.example.chatapp.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authorization = accessor.getNativeHeader("Authorization");
            log.debug("Authorization header: {}", authorization);

            String jwt = null;
            if (authorization != null && !authorization.isEmpty()) {
                // The header is typically "Bearer <token>"
                String authHeader = authorization.get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    jwt = authHeader.substring(7);
                }
            }

            if (jwt != null) {
                try {
                    String username = jwtUtil.extractUsername(jwt);
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                        if (jwtUtil.validateToken(jwt, userDetails)) {
                            UsernamePasswordAuthenticationToken authToken =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(authToken);
                            accessor.setUser(authToken);
                            log.info("WebSocket connection authenticated for user: {}", username);
                        } else {
                            log.warn("Invalid JWT token for WebSocket connection");
                        }
                    }
                } catch (Exception e) {
                    log.error("WebSocket authentication error: ", e);
                }
            } else {
                log.warn("No JWT token found in WebSocket connection");
            }
        }

        return message;
    }
}
