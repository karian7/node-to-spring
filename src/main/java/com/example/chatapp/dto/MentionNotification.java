package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MentionNotification {
    private String messageId;
    private String roomId;
    private String roomName;
    private String senderName;
    private String content;
    private LocalDateTime timestamp;
}
