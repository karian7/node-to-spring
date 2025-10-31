package com.example.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSessionRegistry {

    private static final String USER_SOCKET_KEY_PREFIX = "socketio:userid:";

    private final RedisTemplate<String, Object> redisTemplate;

    public String getSocketIdForUser(String userId) {
        return (String) redisTemplate.opsForValue().get(buildKey(userId));
    }

    public void saveSocketIdForUser(String userId, String socketId) {
        redisTemplate.opsForValue().set(buildKey(userId), socketId);
    }

    public void removeSocketIdForUser(String userId) {
        redisTemplate.delete(buildKey(userId));
    }

    private String buildKey(String userId) {
        return USER_SOCKET_KEY_PREFIX + userId;
    }
}
