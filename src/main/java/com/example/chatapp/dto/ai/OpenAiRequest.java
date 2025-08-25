package com.example.chatapp.dto.ai;

import java.util.List;

public record OpenAiRequest(
    String model,
    List<Message> messages,
    double temperature,
    boolean stream
) {
    public record Message(String role, String content) {
    }
}
