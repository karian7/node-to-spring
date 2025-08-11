package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class TypingEvent {
    private String userId;
    private String userName;
    private String roomId;
    private boolean isTyping;
    private LocalDateTime timestamp;
}
