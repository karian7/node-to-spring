package com.ktb.chatapp.config;

import com.ktb.chatapp.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public RateLimitService rateLimitService() {
        return new RateLimitService(redisTemplate, objectMapper);
    }

    @Slf4j
    public static class RateLimitService {

        private final RedisTemplate<String, String> redisTemplate;
        private final ObjectMapper objectMapper;

        public RateLimitService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
            this.redisTemplate = redisTemplate;
            this.objectMapper = objectMapper;
        }

        // Rate Limit 설정
        private static final int DEFAULT_MAX_REQUESTS = 60; // IP당 최대 요청 수
        private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1); // 1분 윈도우
        private static final String RATE_LIMIT_PREFIX = "rate_limit:";

        /**
         * Rate Limit 체크 및 처리
         */
        public boolean isAllowed(HttpServletRequest request, HttpServletResponse response,
                               int maxRequests, Duration window) throws IOException {

            String clientId = getClientId(request);
            String key = RATE_LIMIT_PREFIX + clientId;

            try {
                // 현재 요청 수 조회
                String countStr = redisTemplate.opsForValue().get(key);
                int currentCount = (countStr != null) ? Integer.parseInt(countStr) : 0;

                if (currentCount >= maxRequests) {
                    // Rate Limit 초과
                    handleRateLimitExceeded(request, response, maxRequests, window);
                    return false;
                }

                // 요청 수 증가
                if (currentCount == 0) {
                    // 첫 번째 요청인 경우 TTL 설정
                    redisTemplate.opsForValue().set(key, "1", window);
                } else {
                    // 기존 TTL 유지하면서 카운트 증가
                    redisTemplate.opsForValue().increment(key);
                }

                // 응답 헤더에 Rate Limit 정보 추가
                addRateLimitHeaders(response, currentCount + 1, maxRequests, window, key);

                return true;

            } catch (Exception e) {
                log.error("Rate limit check failed for client: {}", clientId, e);
                // Rate Limit 체크 실패 시 요청 허용 (Fail Open)
                return true;
            }
        }

        /**
         * 클라이언트 식별자 생성 (IP + User-Agent 기반)
         */
        private String getClientId(HttpServletRequest request) {
            String clientIp = getClientIpAddress(request);
            String userAgent = request.getHeader("User-Agent");

            // IP만 사용하거나 IP + User-Agent 해시 사용
            if (userAgent != null && !userAgent.isEmpty()) {
                return clientIp + ":" + userAgent.hashCode();
            }
            return clientIp;
        }

        /**
         * 클라이언트 IP 주소 추출
         */
        private String getClientIpAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            return request.getRemoteAddr();
        }

        /**
         * Rate Limit 초과 시 응답 처리
         */
        private void handleRateLimitExceeded(HttpServletRequest request, HttpServletResponse response,
                                           int maxRequests, Duration window) throws IOException {

            log.warn("Rate limit exceeded for client: {} on endpoint: {}",
                    getClientId(request), request.getRequestURI());

            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Object> errorResponse = ApiResponse.error(
                "너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.",
                Map.of(
                    "code", "TOO_MANY_REQUESTS",
                    "maxRequests", maxRequests,
                    "windowMs", window.toMillis(),
                    "retryAfter", window.getSeconds()
                )
            );

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        }

        /**
         * Rate Limit 관련 응답 헤더 추가
         */
        private void addRateLimitHeaders(HttpServletResponse response, int currentCount,
                                       int maxRequests, Duration window, String key) {
            try {
                // 남은 요청 수
                int remaining = Math.max(0, maxRequests - currentCount);

                // 윈도우 재설정 시간 (TTL)
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                long resetTime = System.currentTimeMillis() / 1000 + (ttl != null ? ttl : window.getSeconds());

                // 표준 Rate Limit 헤더들 추가
                response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
                response.setHeader("X-RateLimit-Reset", String.valueOf(resetTime));
                response.setHeader("X-RateLimit-Window", String.valueOf(window.getSeconds()));

            } catch (Exception e) {
                log.debug("Failed to add rate limit headers", e);
            }
        }
    }
}
