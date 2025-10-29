package com.example.chatapp.dto;

import com.example.chatapp.model.AiType;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.MessageType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    @JsonProperty("_id")
    private String id;
    @JsonProperty("room")
    private String roomId;
    private String content;
    private UserResponse sender;
    private MessageType type;
    private String fileId;
    private FileResponse file;
    private AiType aiType;
    private List<String> mentions;
    private LocalDateTime timestamp;
    private boolean isDeleted;
    private Map<String, Set<String>> reactions;
    private List<Message.MessageReader> readers;
    private LocalDateTime editedAt;
    private String replyToMessageId;
    private boolean isPinned;
    private LocalDateTime pinnedAt;
    private String pinnedBy;
    private List<String> attachments;
    private Message.FileMetadata metadata;
}
