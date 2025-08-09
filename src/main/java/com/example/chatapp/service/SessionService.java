package com.example.chatapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SessionService {

    private static final String USER_SESSION_PREFIX = "user-session:";
    private static final long SESSION_TIMEOUT_MINUTES = 30;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.opsForValue().set(key, sessionId, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        return sessionId;
    }

    public String getSession(String userId) {
        return redisTemplate.opsForValue().get(USER_SESSION_PREFIX + userId);
    }

    public void removeSession(String userId) {
        redisTemplate.delete(USER_SESSION_PREFIX + userId);
    }

    public boolean isSessionValid(String userId, String sessionId) {
        String storedSessionId = getSession(userId);
        return sessionId != null && sessionId.equals(storedSessionId);
    }

    public void refreshSession(String userId) {
        String key = USER_SESSION_PREFIX + userId;
        redisTemplate.expire(key, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }
}
