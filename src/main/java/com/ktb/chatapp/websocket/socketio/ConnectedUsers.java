package com.ktb.chatapp.websocket.socketio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ConnectedUsers {
    
    private static final String USER_SOCKET_KEY_PREFIX = "conn_users:userid:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public SocketUser get(String userId) {
        var json = redisTemplate.opsForValue().get(buildKey(userId));
        if (json == null) {
            return null;
        }
        return fromJson((String) json);
    }
    
    public void set(String userId, SocketUser sockerUser) {
        redisTemplate.opsForValue().set(buildKey(userId), toJson(sockerUser));
    }
    
    public void del(String userId) {
        redisTemplate.delete(buildKey(userId));
    }
    
    private String buildKey(String userId) {
        return USER_SOCKET_KEY_PREFIX + userId;
    }
    
    private SocketUser fromJson(String json) {
        try {
            return objectMapper.readValue(json, SocketUser.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private String toJson(SocketUser sockerUser) {
        try {
            return objectMapper.writeValueAsString(sockerUser);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
