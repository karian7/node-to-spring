package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for joinRoomSuccess event
 * Matches Node.js backend's joinRoomSuccess response structure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinRoomSuccessResponse {
    private String roomId;
    private List<UserDto> participants;
    private List<MessageResponse> messages;
    private boolean hasMore;
    private LocalDateTime oldestTimestamp;
    private List<ActiveStreamResponse> activeStreams;
}
