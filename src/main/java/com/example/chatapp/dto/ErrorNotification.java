package com.example.chatapp.dto;

import java.time.LocalDateTime;

public record ErrorNotification(String type, String message, LocalDateTime timestamp) {
}
