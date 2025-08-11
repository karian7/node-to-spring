package com.example.chatapp.dto;

import java.time.LocalDateTime;

public record DeliveryConfirmation(String messageId, LocalDateTime timestamp, String status) {
    // status: "DELIVERED", "READ", "FAILED"
}
