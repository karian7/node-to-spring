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

    // ChatController에서 사용하는 생성자 (String userId, List<String> messageIds)
    public MessagesReadResponse(String userId, List<String> messageIds) {
        this.success = true;
        this.message = "Messages marked as read for user: " + userId;
        this.lastReadMessageId = messageIds != null && !messageIds.isEmpty() ? messageIds.get(messageIds.size() - 1) : null;
    }
}
