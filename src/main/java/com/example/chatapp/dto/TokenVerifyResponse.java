package com.example.chatapp.dto;

public record TokenVerifyResponse(
    boolean success,
    String message,
    UserDto user
) {
}
