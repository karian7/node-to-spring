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

    /**
     * 의심스러운 세션 활동 감지
     */
    public SuspiciousActivityResult detectSuspiciousActivity(String userId, SessionMetadata newMetadata) {
        try {
            SessionData currentSession = getActiveSession(userId);
            if (currentSession == null) {
                return new SuspiciousActivityResult(false, "정상");
            }

            SessionMetadata currentMetadata = currentSession.getMetadata();
            if (currentMetadata == null) {
                return new SuspiciousActivityResult(false, "정상");
            }

            // IP 주소 변경 감지
            boolean ipChanged = !Objects.equals(currentMetadata.getIpAddress(), newMetadata.getIpAddress());

            // User-Agent 변경 감지
            boolean userAgentChanged = !Objects.equals(currentMetadata.getUserAgent(), newMetadata.getUserAgent());

            if (ipChanged && userAgentChanged) {
                log.warn("Suspicious activity detected for user {}: IP and User-Agent both changed", userId);
                return new SuspiciousActivityResult(true, "IP 주소와 브라우저 정보가 모두 변경됨");
            }

            if (ipChanged) {
                log.info("IP address changed for user {}: {} -> {}",
                        userId, currentMetadata.getIpAddress(), newMetadata.getIpAddress());
                return new SuspiciousActivityResult(true, "IP 주소 변경 감지");
            }

            return new SuspiciousActivityResult(false, "정상");

        } catch (Exception e) {
            log.error("Error detecting suspicious activity for user: {}", userId, e);
            return new SuspiciousActivityResult(false, "정상");
        }
    }

    /**
     * 세션 보안 강화: 동시 접속 제한
     */
    public SessionValidationResult validateSessionWithSecurity(String userId, String sessionId,
                                                             SessionMetadata currentMetadata) {
        try {
            // 기본 세션 검증
            SessionValidationResult basicResult = validateSession(userId, sessionId);
            if (!basicResult.isValid()) {
                return basicResult;
            }

            // 의심스러운 활동 감지
            SuspiciousActivityResult suspiciousResult = detectSuspiciousActivity(userId, currentMetadata);
            if (suspiciousResult.isSuspicious()) {
                log.warn("Suspicious session activity for user {}: {}", userId, suspiciousResult.getReason());

                // 의심스러운 활동 시 추가 검증 수행
                return handleSuspiciousActivity(userId, sessionId, suspiciousResult);
            }

            return basicResult;

        } catch (Exception e) {
            log.error("Error in enhanced session validation for user: {}", userId, e);
            return SessionValidationResult.invalid("VALIDATION_ERROR", "세션 검증 중 오류가 발생했습니다.");
        }
    }

    /**
     * 의심스러운 활동 처리
     */
    private SessionValidationResult handleSuspiciousActivity(String userId, String sessionId,
                                                           SuspiciousActivityResult suspiciousResult) {
        // 중요도에 따라 다른 처리
        if (suspiciousResult.getReason().contains("IP 주소와 브라우저")) {
            // 매우 의심스러운 경우: 세션 무효화
            removeAllUserSessions(userId);
            return SessionValidationResult.invalid("SUSPICIOUS_ACTIVITY",
                "보안상의 이유로 세션이 무효화되었습니다. 다시 로그인해주세요.");
        } else {
            // 덜 의심스러운 경우: 경고만 로그
            log.warn("Suspicious activity detected but session maintained: {}", suspiciousResult.getReason());
            return SessionValidationResult.valid(getActiveSession(userId));
        }
    }

    /**
     * 세션 만료 시간 동적 조정
     */
    public void adjustSessionTTL(String userId, String sessionId, SessionActivity activity) {
        try {
            String sessionKey = getSessionKey(userId);
            SessionData sessionData = getJson(sessionKey, SessionData.class);

            if (sessionData == null) return;

            // 활동 유형에 따른 TTL 조정
            long newTTL = calculateDynamicTTL(activity, sessionData);

            if (newTTL != SESSION_TTL) {
                // TTL 업데이트
                setJson(sessionKey, sessionData, newTTL);

                // 관련 키들도 업데이트
                redisTemplate.expire(getActiveSessionKey(userId), Duration.ofSeconds(newTTL));
                redisTemplate.expire(getUserSessionsKey(userId), Duration.ofSeconds(newTTL));
                redisTemplate.expire(getSessionIdKey(sessionId), Duration.ofSeconds(newTTL));

                log.debug("Session TTL adjusted for user {}: {} seconds", userId, newTTL);
            }

        } catch (Exception e) {
            log.error("Error adjusting session TTL for user: {}", userId, e);
        }
    }

    /**
     * 활동 유형에 따른 동적 TTL 계산
     */
    private long calculateDynamicTTL(SessionActivity activity, SessionData sessionData) {
        switch (activity) {
            case HIGH_SECURITY_ACTION:
                // 중요한 작업 후에는 TTL 단축
                return Math.min(SESSION_TTL, 30 * 60); // 최대 30분
            case REGULAR_ACTIVITY:
                return SESSION_TTL;
            case EXTENDED_SESSION:
                // 장시간 활동 시 TTL 연장
                return SESSION_TTL * 2; // 48시간
            default:
                return SESSION_TTL;
        }
    }

    /**
     * 강제 로그아웃 (보안 목적)
     */
    public void forceLogout(String userId, String reason) {
        try {
            removeAllUserSessions(userId);
            log.warn("Forced logout for user {}: {}", userId, reason);

            // 강제 로그아웃 이벤트 기록 (필요시 별도 서비스로 분리)
            recordSecurityEvent(userId, "FORCED_LOGOUT", reason);

        } catch (Exception e) {
            log.error("Error during forced logout for user: {}", userId, e);
        }
    }

    /**
     * 보안 이벤트 기록
     */
    private void recordSecurityEvent(String userId, String eventType, String details) {
        try {
            String eventKey = "security_event:" + userId + ":" + System.currentTimeMillis();
            SecurityEvent event = new SecurityEvent(userId, eventType, details, Instant.now());
            setJson(eventKey, event, 7 * 24 * 60 * 60); // 7일 보관
        } catch (Exception e) {
            log.error("Failed to record security event", e);
        }
    }

    /**
     * 최근 보안 이벤트 조회
     */
    public List<SecurityEvent> getRecentSecurityEvents(String userId, int limit) {
        try {
            // Redis pattern 검색을 통한 보안 이벤트 조회
            // 실제 구현에서는 더 효율적인 방법 고려 필요
            String pattern = "security_event:" + userId + ":*";

            // 간단한 구현 예시 (실제로는 별도 저장소 사용 권장)
            return new ArrayList<>(); // 실제 구현 필요

        } catch (Exception e) {
            log.error("Error retrieving security events for user: {}", userId, e);
            return new ArrayList<>();
        }
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

    /**
     * 의심스러운 활동 감지 결과
     */
    @AllArgsConstructor
    @Getter
    public static class SuspiciousActivityResult {
        private final boolean suspicious;
        private final String reason;
    }

    /**
     * 세션 활동 유형
     */
    public enum SessionActivity {
        REGULAR_ACTIVITY,
        HIGH_SECURITY_ACTION,
        EXTENDED_SESSION
    }

    /**
     * 보안 이벤트 데이터
     */
    @AllArgsConstructor
    @Getter
    public static class SecurityEvent {
        private String userId;
        private String eventType;
        private String details;
        private Instant timestamp;
    }
}
