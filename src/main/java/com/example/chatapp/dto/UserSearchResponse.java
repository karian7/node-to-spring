package com.example.chatapp.dto;

import java.util.List;

public record UserSearchResponse(
    boolean success,
    String message,
    List<UserSummaryResponse> users,
    int totalPages,
    long totalElements,
    int currentPage
) {
}
