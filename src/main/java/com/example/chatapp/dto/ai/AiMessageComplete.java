package com.example.chatapp.dto.ai;

import com.example.chatapp.model.AiType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageComplete {
    private String messageId; // The temporary ID used during streaming
    private String _id; // The final ID from the database
    private String content;
    private AiType aiType;
    private LocalDateTime timestamp;
    private boolean isComplete;
    private String query;
    private Map<String, Set<String>> reactions;
}
