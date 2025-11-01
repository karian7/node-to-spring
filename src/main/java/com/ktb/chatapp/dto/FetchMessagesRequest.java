package com.ktb.chatapp.dto;

public record FetchMessagesRequest(String roomId, int limit) {
}
