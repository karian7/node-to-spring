package com.example.chatapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Field("roomId")
    private String roomId;

    private String content;

    @Field("senderId")
    private String senderId;

    private MessageType type;

    @Field("fileId")
    private String fileId;

    private AiType aiType;

    private List<String> mentions;

    @CreatedDate
    private LocalDateTime timestamp;

    @Builder.Default
    private boolean isDeleted = false;

    private Map<String, Set<String>> reactions;

    private List<ReaderInfo> readers;

    private FileMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReaderInfo {
        private String userId;
        private LocalDateTime readAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileMetadata {
        private String fileType;
        private long fileSize;
        private String originalName;
    }
}
