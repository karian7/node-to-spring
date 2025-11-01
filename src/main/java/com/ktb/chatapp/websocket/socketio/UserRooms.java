package com.ktb.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-based registry for tracking which room each user is currently in.
 * Maps userId -> roomId to maintain user room state across the application.
 */
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class UserRooms {

    private static final String USER_ROOM_KEY_PREFIX = "userroom:roomid:";

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get the current room ID for a user
     *
     * @param userId the user ID
     * @return the room ID the user is currently in, or null if not in any room
     */
    public String get(String userId) {
        return (String) redisTemplate.opsForValue().get(buildKey(userId));
    }

    /**
     * Save the room ID for a user
     *
     * @param userId the user ID
     * @param roomId the room ID to associate with the user
     */
    public void set(String userId, String roomId) {
        redisTemplate.opsForValue().set(buildKey(userId), roomId);
    }

    /**
     * Remove the room association for a user
     *
     * @param userId the user ID
     */
    public void del(String userId) {
        redisTemplate.delete(buildKey(userId));
    }

    private String buildKey(String userId) {
        return USER_ROOM_KEY_PREFIX + userId;
    }
}

