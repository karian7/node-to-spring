package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public record AiMessageComplete(
    String messageId, // The temporary ID used during streaming
    String _id, // The final ID from the database
    String content,
    AiType aiType,
    LocalDateTime timestamp,
    boolean isComplete,
    String query,
    Map<String, Set<String>> reactions
) {
}
