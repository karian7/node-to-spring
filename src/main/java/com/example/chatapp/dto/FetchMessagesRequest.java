package com.example.chatapp.dto;

import java.time.LocalDateTime;

public record FetchMessagesRequest(String roomId, LocalDateTime before) {
}
