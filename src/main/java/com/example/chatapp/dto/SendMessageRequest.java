package com.example.chatapp.dto;

import com.example.chatapp.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    private String content;
    private MessageType type;
    private String fileId;
}
