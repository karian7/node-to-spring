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

    // 기존 생성자와의 호환성을 위한 추가 생성자
    public MessageReactionResponse(String messageId, Map<String, Set<String>> reactions) {
        this.success = true;
        this.message = messageId;
        this.reactions = reactions;
    }
}
