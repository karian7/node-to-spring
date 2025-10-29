package com.example.chatapp.websocket.socketio;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingSession {
    private String messageId;
    private String roomId;
    private String userId;
    private String aiType;
    private String content;
    private LocalDateTime timestamp;
    private long lastUpdate;
    private boolean isStreaming;
}
