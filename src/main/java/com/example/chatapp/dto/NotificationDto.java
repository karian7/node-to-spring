package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String type;
    private Object data;
    private LocalDateTime timestamp;
    private String priority = "medium"; // low, medium, high, critical
    private boolean read = false;
}
