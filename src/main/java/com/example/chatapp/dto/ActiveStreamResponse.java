package com.example.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Active AI streaming session 응답 DTO.
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
    private String timestamp;  // ISO_INSTANT 형식 문자열
    private boolean isStreaming;
}
