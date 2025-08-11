package com.example.chatapp.dto;

import java.time.LocalDateTime;

public record MentionNotification(
    String messageId,
    String roomId,
    String roomName,
    String senderName,
    String content,
    LocalDateTime timestamp
) {
}
