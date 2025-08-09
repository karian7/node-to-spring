package com.example.chatapp.dto;

import lombok.Data;

@Data
public class MessageReactionRequest {
    private String messageId;
    private String reaction;
    private String type; // "add" or "remove"
}
