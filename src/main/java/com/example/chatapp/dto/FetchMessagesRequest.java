package com.example.chatapp.dto;

public record FetchMessagesRequest(String roomId, int limit) {
}
