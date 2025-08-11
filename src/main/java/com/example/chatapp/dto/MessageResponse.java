package com.example.chatapp.dto;

import com.example.chatapp.model.AiType;
import com.example.chatapp.model.MessageType;
import com.example.chatapp.model.Message;
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
    private String id;
    private String roomId;
    private String content;
    private UserResponse sender;
    private MessageType type;
    private FileResponse file;
    private AiType aiType;
    private List<String> mentions;
    private LocalDateTime timestamp;

    // 노드 버전과 동일한 필드들 추가
    private Map<String, Set<String>> reactions;
    private List<Message.MessageReader> readers;
    private Message.FileMetadata metadata;

    // 기존 필드들 (호환성 유지)
    private int readCount;
    private boolean isDeleted;
}
