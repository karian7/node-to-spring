package com.example.chatapp.websocket.socketio.handler;

import java.time.Instant;
import java.time.ZoneId;
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
    
    public String timestampToString() {
        String timestampStr = null;
        if (getTimestamp() != null) {
            Instant instant = getTimestamp()
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
            timestampStr = instant.toString();
        }
        return timestampStr;
    }
}
