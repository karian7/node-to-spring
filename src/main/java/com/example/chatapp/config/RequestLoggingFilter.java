package com.example.chatapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * 요청 로깅 필터
 * 개발 모드에서만 요청 메서드와 URI를 로깅
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Value("${spring.profiles.active:production}")
    private String profile;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isDevelopmentMode()) {
            log.info("[{}] {} {}",
                    Instant.now().toString(),
                    request.getMethod(),
                    request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isDevelopmentMode() {
        if (profile != null && !profile.isBlank()) {
            String normalized = profile.trim().toLowerCase();
            if (normalized.contains("dev")) {
                return true;
            }
            if ("development".equals(normalized)) {
                return true;
            }
        }
        String nodeEnv = System.getenv("NODE_ENV");
        return nodeEnv != null && nodeEnv.equalsIgnoreCase("development");
    }
}
