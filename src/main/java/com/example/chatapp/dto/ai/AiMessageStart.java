package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageStart {
    private String messageId;
    private AiType aiType;
    private LocalDateTime timestamp;
}
