package com.example.chatapp.websocket.socketio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Streaming session management class
 * Matches Node.js backend's streamingSessions structure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreamingSession {
    private String roomId;
    private String aiType;
    private String content;
    private String messageId;
    private LocalDateTime timestamp;
    private long lastUpdate;
    private String userId;
}
