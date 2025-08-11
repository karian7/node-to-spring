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
    private String id;
    private String roomId;
    private String content;
    private UserSummaryResponse sender;
    private MessageType type;
    private FileUploadResponse file;
    private AiType aiType;
    private List<String> mentions;
    private LocalDateTime timestamp;

    // 읽음 상태 관리를 위한 필드 추가
    private int readCount;
    private List<ReaderInfo> readers;
    private boolean isDeleted;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReaderInfo {
        private String userId;
        private String userName;
        private LocalDateTime readAt;
    }
}
