package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageChunk {
    private String messageId;
    private String currentChunk;
    private String fullContent;
    private boolean isCodeBlock;
    private LocalDateTime timestamp;
    private AiType aiType;
    private boolean isComplete;
}
