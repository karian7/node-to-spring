package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessagesReadResponse {
    private boolean success;
    private String message;
    private String lastReadMessageId;

    // 기존 생성자와의 호환성을 위한 추가 생성자
    public MessagesReadResponse(String userId, List<String> messageIds) {
        this.success = true;
        this.message = userId;
        this.lastReadMessageId = messageIds != null && !messageIds.isEmpty() ? messageIds.get(messageIds.size() - 1) : null;
    }
}
