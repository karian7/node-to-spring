package com.example.chatapp.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FetchMessagesRequest {
    private String roomId;
    private LocalDateTime before;
}
