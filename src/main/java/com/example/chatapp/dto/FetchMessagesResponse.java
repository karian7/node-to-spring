package com.example.chatapp.dto;

import java.util.List;

public record FetchMessagesResponse(
    List<MessageResponse> messages,
    boolean hasMore
) {
}
