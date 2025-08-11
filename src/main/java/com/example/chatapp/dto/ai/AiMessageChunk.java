package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import java.time.LocalDateTime;

public record AiMessageChunk(
    String messageId,
    String currentChunk,
    String fullContent,
    boolean isCodeBlock,
    LocalDateTime timestamp,
    AiType aiType,
    boolean isComplete
) {
}
