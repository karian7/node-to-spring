package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import java.time.LocalDateTime;

public record AiMessageStart(
    String messageId,
    AiType aiType,
    LocalDateTime timestamp
) {
}
