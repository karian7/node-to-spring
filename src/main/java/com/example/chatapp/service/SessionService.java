package com.example.chatapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final long SESSION_TTL = 24 * 60 * 60; // 24 hours in seconds
    private static final String SESSION_PREFIX = "session:";
    private static final String SESSION_ID_PREFIX = "sessionId:";
    private static final String USER_SESSIONS_PREFIX = "user_sessions:";
    private static final String ACTIVE_SESSION_PREFIX = "active_session:";
    private static final long SESSION_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Key generation methods
    private String getSessionKey(String userId) {
        return SESSION_PREFIX + userId;
    }

    private String getSessionIdKey(String sessionId) {
        return SESSION_ID_PREFIX + sessionId;
    }

    private String getUserSessionsKey(String userId) {
        return USER_SESSIONS_PREFIX + userId;
    }

    private String getActiveSessionKey(String userId) {
        return ACTIVE_SESSION_PREFIX + userId;
    }

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // Safe JSON operations
    private boolean setJson(String key, Object value, long ttlSeconds) {
        try {
            String jsonString = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, jsonString, Duration.ofSeconds(ttlSeconds));
            return true;
        } catch (JsonProcessingException e) {
            log.error("JSON serialization error for key: {}", key, e);
            return false;
        }
    }

    private <T> T getJson(String key, Class<T> clazz) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON deserialization error for key: {}", key, e);
            return null;
        }
    }

    public SessionCreationResult createSession(String userId, SessionMetadata metadata) {
        try {
            // Remove all existing user sessions
            removeAllUserSessions(userId);

            String sessionId = generateSessionId();
            SessionData sessionData = SessionData.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .createdAt(Instant.now().toEpochMilli())
                    .lastActivity(Instant.now().toEpochMilli())
                    .metadata(metadata)
                    .build();

            String sessionKey = getSessionKey(userId);
            String sessionIdKey = getSessionIdKey(sessionId);
            String userSessionsKey = getUserSessionsKey(userId);
            String activeSessionKey = getActiveSessionKey(userId);

            // Save session data
            boolean saved = setJson(sessionKey, sessionData, SESSION_TTL);
            if (!saved) {
                throw new RuntimeException("세션 데이터 저장에 실패했습니다.");
            }

            // Save session ID mappings
            redisTemplate.opsForValue().set(sessionIdKey, userId, Duration.ofSeconds(SESSION_TTL));
            redisTemplate.opsForValue().set(userSessionsKey, sessionId, Duration.ofSeconds(SESSION_TTL));
            redisTemplate.opsForValue().set(activeSessionKey, sessionId, Duration.ofSeconds(SESSION_TTL));

            return SessionCreationResult.builder()
                    .sessionId(sessionId)
                    .expiresIn(SESSION_TTL)
                    .sessionData(sessionData)
                    .build();

        } catch (Exception e) {
            log.error("Session creation error for userId: {}", userId, e);
            throw new RuntimeException("세션 생성 중 오류가 발생했습니다.", e);
        }
    }

    public SessionValidationResult validateSession(String userId, String sessionId) {
        try {
            if (userId == null || sessionId == null) {
                return SessionValidationResult.invalid("INVALID_PARAMETERS", "유효하지 않은 세션 파라미터");
            }

            // Check active session
            String activeSessionKey = getActiveSessionKey(userId);
            String activeSessionId = redisTemplate.opsForValue().get(activeSessionKey);

            if (activeSessionId == null || !activeSessionId.equals(sessionId)) {
                log.debug("Session validation failed - userId: {}, sessionId: {}, activeSessionId: {}",
                         userId, sessionId, activeSessionId);
                return SessionValidationResult.invalid("INVALID_SESSION",
                    "다른 기기에서 로그인되어 현재 세션이 만료되었습니다.");
            }

            // Validate session data
            String sessionKey = getSessionKey(userId);
            SessionData sessionData = getJson(sessionKey, SessionData.class);

            if (sessionData == null) {
                return SessionValidationResult.invalid("SESSION_NOT_FOUND", "세션을 찾을 수 없습니다.");
            }

            // Check session timeout
            if (System.currentTimeMillis() - sessionData.getLastActivity() > SESSION_TIMEOUT) {
                removeSession(userId);
                return SessionValidationResult.invalid("SESSION_EXPIRED", "세션이 만료되었습니다.");
            }

            // Update session activity
            sessionData.setLastActivity(System.currentTimeMillis());

            // Save updated session data
            boolean updated = setJson(sessionKey, sessionData, SESSION_TTL);
            if (!updated) {
                return SessionValidationResult.invalid("UPDATE_FAILED", "세션 갱신에 실패했습니다.");
            }

            // Update expiration times for related keys
            redisTemplate.expire(activeSessionKey, Duration.ofSeconds(SESSION_TTL));
            redisTemplate.expire(getUserSessionsKey(userId), Duration.ofSeconds(SESSION_TTL));
            redisTemplate.expire(getSessionIdKey(sessionId), Duration.ofSeconds(SESSION_TTL));

            return SessionValidationResult.valid(sessionData);

        } catch (Exception e) {
            log.error("Session validation error for userId: {}, sessionId: {}", userId, sessionId, e);
            return SessionValidationResult.invalid("VALIDATION_ERROR", "세션 검증 중 오류가 발생했습니다.");
        }
    }

    public void updateLastActivity(String userId) {
        try {
            if (userId == null) {
                log.warn("updateLastActivity called with null userId");
                return;
            }

            String sessionKey = getSessionKey(userId);
            SessionData sessionData = getJson(sessionKey, SessionData.class);
            if (sessionData == null) {
                log.debug("No session found to update last activity for user: {}", userId);
                return;
            }

            sessionData.setLastActivity(Instant.now().toEpochMilli());

            boolean updated = setJson(sessionKey, sessionData, SESSION_TTL);
            if (!updated) {
                log.warn("Failed to persist session activity for user: {}", userId);
                return;
            }

            redisTemplate.expire(getActiveSessionKey(userId), Duration.ofSeconds(SESSION_TTL));
            redisTemplate.expire(getUserSessionsKey(userId), Duration.ofSeconds(SESSION_TTL));
            redisTemplate.expire(getSessionIdKey(sessionData.getSessionId()), Duration.ofSeconds(SESSION_TTL));
        } catch (Exception e) {
            log.error("Failed to update session activity for user: {}", userId, e);
        }
    }

    public void removeSession(String userId, String sessionId) {
        try {
            String userSessionsKey = getUserSessionsKey(userId);
            String activeSessionKey = getActiveSessionKey(userId);

            if (sessionId != null) {
                String currentSessionId = redisTemplate.opsForValue().get(userSessionsKey);
                if (sessionId.equals(currentSessionId)) {
                    removeSessionKeys(userId, sessionId, userSessionsKey, activeSessionKey);
                }
            } else {
                String storedSessionId = redisTemplate.opsForValue().get(userSessionsKey);
                if (storedSessionId != null) {
                    removeSessionKeys(userId, storedSessionId, userSessionsKey, activeSessionKey);
                }
            }
        } catch (Exception e) {
            log.error("Session removal error for userId: {}, sessionId: {}", userId, sessionId, e);
            throw new RuntimeException("세션 삭제 중 오류가 발생했습니다.", e);
        }
    }

    public void removeSession(String userId) {
        removeSession(userId, null);
    }

    public void removeAllUserSessions(String userId) {
        try {
            String activeSessionKey = getActiveSessionKey(userId);
            String userSessionsKey = getUserSessionsKey(userId);
            String sessionId = redisTemplate.opsForValue().get(userSessionsKey);

            redisTemplate.delete(activeSessionKey);
            redisTemplate.delete(userSessionsKey);

            if (sessionId != null) {
                redisTemplate.delete(getSessionKey(userId));
                redisTemplate.delete(getSessionIdKey(sessionId));
            }
        } catch (Exception e) {
            log.error("Remove all sessions error for userId: {}", userId, e);
            throw new RuntimeException("모든 세션 삭제 중 오류가 발생했습니다.", e);
        }
    }

    public SessionData getActiveSession(String userId) {
        try {
            String activeSessionKey = getActiveSessionKey(userId);
            String sessionId = redisTemplate.opsForValue().get(activeSessionKey);

            if (sessionId == null) {
                return null;
            }

            String sessionKey = getSessionKey(userId);
            return getJson(sessionKey, SessionData.class);
        } catch (Exception e) {
            log.error("Get active session error for userId: {}", userId, e);
            return null;
        }
    }

    private void removeSessionKeys(String userId, String sessionId, String userSessionsKey, String activeSessionKey) {
        redisTemplate.delete(getSessionKey(userId));
        redisTemplate.delete(getSessionIdKey(sessionId));
        redisTemplate.delete(userSessionsKey);
        redisTemplate.delete(activeSessionKey);
    }

    // Data classes
    public static class SessionData {
        private String userId;
        private String sessionId;
        private long createdAt;
        private long lastActivity;
        private SessionMetadata metadata;

        // Constructor
        public SessionData() {}

        public static SessionDataBuilder builder() {
            return new SessionDataBuilder();
        }

        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        public long getLastActivity() { return lastActivity; }
        public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }

        public SessionMetadata getMetadata() { return metadata; }
        public void setMetadata(SessionMetadata metadata) { this.metadata = metadata; }

        public static class SessionDataBuilder {
            private String userId;
            private String sessionId;
            private long createdAt;
            private long lastActivity;
            private SessionMetadata metadata;

            public SessionDataBuilder userId(String userId) { this.userId = userId; return this; }
            public SessionDataBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public SessionDataBuilder createdAt(long createdAt) { this.createdAt = createdAt; return this; }
            public SessionDataBuilder lastActivity(long lastActivity) { this.lastActivity = lastActivity; return this; }
            public SessionDataBuilder metadata(SessionMetadata metadata) { this.metadata = metadata; return this; }

            public SessionData build() {
                SessionData data = new SessionData();
                data.userId = this.userId;
                data.sessionId = this.sessionId;
                data.createdAt = this.createdAt;
                data.lastActivity = this.lastActivity;
                data.metadata = this.metadata;
                return data;
            }
        }
    }

    public static class SessionMetadata {
        private String userAgent;
        private String ipAddress;
        private String deviceInfo;
        private long createdAt;

        public SessionMetadata() {}

        public SessionMetadata(String userAgent, String ipAddress, String deviceInfo) {
            this.userAgent = userAgent;
            this.ipAddress = ipAddress;
            this.deviceInfo = deviceInfo;
            this.createdAt = System.currentTimeMillis();
        }

        // Getters and Setters
        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getDeviceInfo() { return deviceInfo; }
        public void setDeviceInfo(String deviceInfo) { this.deviceInfo = deviceInfo; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class SessionCreationResult {
        private String sessionId;
        private long expiresIn;
        private SessionData sessionData;


        public static SessionCreationResultBuilder builder() {
            return new SessionCreationResultBuilder();
        }

        // Getters and Setters
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public long getExpiresIn() { return expiresIn; }
        public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

        public SessionData getSessionData() { return sessionData; }
        public void setSessionData(SessionData sessionData) { this.sessionData = sessionData; }

        public static class SessionCreationResultBuilder {
            private String sessionId;
            private long expiresIn;
            private SessionData sessionData;

            public SessionCreationResultBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
            public SessionCreationResultBuilder expiresIn(long expiresIn) { this.expiresIn = expiresIn; return this; }
            public SessionCreationResultBuilder sessionData(SessionData sessionData) { this.sessionData = sessionData; return this; }

            public SessionCreationResult build() {
                SessionCreationResult result = new SessionCreationResult();
                result.sessionId = this.sessionId;
                result.expiresIn = this.expiresIn;
                result.sessionData = this.sessionData;
                return result;
            }
        }
    }

    @NoArgsConstructor
    @Getter
    @Setter
    public static class SessionValidationResult {
        private boolean isValid;
        private String error;
        private String message;
        private SessionData session;

        public static SessionValidationResult valid(SessionData session) {
            SessionValidationResult result = new SessionValidationResult();
            result.isValid = true;
            result.session = session;
            return result;
        }

        public static SessionValidationResult invalid(String error, String message) {
            SessionValidationResult result = new SessionValidationResult();
            result.isValid = false;
            result.error = error;
            result.message = message;
            return result;
        }
    }
}
