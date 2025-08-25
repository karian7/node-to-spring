package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageReactionResponse {
    private boolean success;
    private String message;
    private Map<String, Set<String>> reactions;

    // ChatController에서 사용하는 생성자 (String messageId, Map reactions)
    public MessageReactionResponse(String messageId, Map<String, Set<String>> reactions) {
        this.success = true;
        this.message = "Reaction updated for message: " + messageId;
        this.reactions = reactions;
    }
}
