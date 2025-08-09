package com.example.chatapp.dto;

import com.example.chatapp.model.AiType;
import com.example.chatapp.model.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private Long id;
    private Long roomId;
    private String content;
    private UserSummaryResponse sender;
    private MessageType type;
    private FileUploadResponse file;
    private AiType aiType;
    private List<String> mentions;
    private LocalDateTime timestamp;
}
