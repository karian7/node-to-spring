package com.example.chatapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class SessionService {

    private static final String USER_SESSION_PREFIX = "user-session:";
    private static final String SESSION_USER_PREFIX = "session-user:";
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    private final RedisTemplate<String, String> redisTemplate;

    public String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        String userKey = USER_SESSION_PREFIX + userId;
        String sessionKey = SESSION_USER_PREFIX + sessionId;

        // 기존 세션이 있다면 제거
        String oldSessionId = redisTemplate.opsForValue().get(userKey);
        if (oldSessionId != null) {
            redisTemplate.delete(SESSION_USER_PREFIX + oldSessionId);
        }

        // 새 세션 생성
        redisTemplate.opsForValue().set(userKey, sessionId, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(sessionKey, userId, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        return sessionId;
    }

    // 세션 ID로 유효성 검증
    public boolean isValidSession(String sessionId) {
        if (sessionId == null) return false;
        String userId = redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId);
        return userId != null;
    }

    // 세션 ID로 사용자 ID 조회
    public String getUserIdFromSession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId);
    }

    // 세션 갱신 (세션 ID 기반)
    public void refreshSessionById(String sessionId) {
        String userId = getUserIdFromSession(sessionId);
        if (userId != null) {
            String userKey = USER_SESSION_PREFIX + userId;
            String sessionKey = SESSION_USER_PREFIX + sessionId;

            redisTemplate.expire(userKey, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            redisTemplate.expire(sessionKey, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        }
    }

    // 세션 무효화 (세션 ID 기반)
    public boolean invalidateSession(String sessionId) {
        String userId = getUserIdFromSession(sessionId);
        if (userId != null) {
            String userKey = USER_SESSION_PREFIX + userId;
            String sessionKey = SESSION_USER_PREFIX + sessionId;

            redisTemplate.delete(userKey);
            redisTemplate.delete(sessionKey);
            return true;
        }
        return false;
    }
}
