package com.example.chatapp.dto;

public record TokenRefreshRequest(String token, String sessionId) {
}
