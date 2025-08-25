package com.example.chatapp.dto;

import java.util.List;

public record ParticipantListResponse(
    String roomId,
    List<UserResponse> participants
) {
}
