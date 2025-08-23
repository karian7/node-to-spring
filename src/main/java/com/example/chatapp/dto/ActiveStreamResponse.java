package com.example.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for active AI streaming sessions
 * Matches Node.js backend's activeStreams structure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActiveStreamResponse {
    @JsonProperty("_id")
    private String id;
    private String type;
    private String aiType;
    private String content;
    private LocalDateTime timestamp;
    private boolean isStreaming;
}
