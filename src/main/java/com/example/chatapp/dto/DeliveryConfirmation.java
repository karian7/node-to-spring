package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryConfirmation {
    private String messageId;
    private LocalDateTime timestamp;
    private String status; // "DELIVERED", "READ", "FAILED"
}
