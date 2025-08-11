package com.example.chatapp.security;

import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Support both standard Authorization header and custom headers
        String jwt = extractJwtToken(request);
        String sessionId = extractSessionId(request);
        String userId = null;

        if (jwt != null) {
            try {
                userId = jwtUtil.extractSubject(jwt);
            } catch (Exception e) {
                log.debug("JWT token extraction failed: {}", e.getMessage());
            }
        }

        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userId);

            // Validate JWT token
            if (jwtUtil.validateToken(jwt, userDetails)) {

                // Additional session validation if sessionId is provided
                if (sessionId != null) {
                    SessionService.SessionValidationResult validationResult =
                            sessionService.validateSession(userDetails.getUsername(), sessionId);

                    if (!validationResult.isValid()) {
                        log.debug("Session validation failed: {} - {}",
                                validationResult.getError(), validationResult.getMessage());
                        // Continue with JWT-only authentication
                    }
                }

                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Extract JWT token from multiple header sources
     */
    private String extractJwtToken(HttpServletRequest request) {
        // 1. Try custom header first (Node.js compatibility)
        String token = request.getHeader("x-auth-token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 2. Try query parameter (for WebSocket connections)
        token = request.getParameter("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }

        // 3. Try standard Authorization header
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        return null;
    }

    /**
     * Extract session ID from multiple header sources
     */
    private String extractSessionId(HttpServletRequest request) {
        // 1. Try custom header first (Node.js compatibility)
        String sessionId = request.getHeader("x-session-id");
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }

        // 2. Try query parameter (for WebSocket connections)
        sessionId = request.getParameter("sessionId");
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }

        return null;
    }
}
