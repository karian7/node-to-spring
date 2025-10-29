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
import java.util.ArrayList;
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

    // 메시지 읽음 상태 관리 필드들 추가
    @Builder.Default
    private List<MessageReader> readers = new ArrayList<>();

    private LocalDateTime editedAt;

    private String replyToMessageId;

    @Builder.Default
    private boolean isPinned = false;

    private LocalDateTime pinnedAt;

    private String pinnedBy;

    // AI 응답 관련 필드들
    private String aiPrompt;

    private AiMetadata aiMetadata;

    // 파일 첨부 관련 추가 정보
    @Builder.Default
    private List<String> attachments = new ArrayList<>();
    private FileMetadata metadata;


    // 메시지 읽음 상태를 나타내는 내부 클래스
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReader {
        private String userId;
        private LocalDateTime readAt;
        private String deviceId; // 멀티 디바이스 지원
    }

    @Builder
    public static class FileMetadata {
        private String fileName;
        private long fileSize;
        private String fileType;
        private String url; // 파일 접근 URL
        private String originalName; // 원본 파일 이름
    }

    @Builder
    public static class AiMetadata {
        private String query;
        private long generationTime;
    }
}
