package com.example.chatapp.dto;

import com.example.chatapp.model.AiType;
import com.example.chatapp.model.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotNull
    private String roomId;

    private String content;

    @NotNull
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private String fileId;

    private AiType aiType;

    private List<String> mentions;
}
