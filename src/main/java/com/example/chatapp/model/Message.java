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

    // 읽음 상태 관리를 위한 readers 필드 추가
    @Builder.Default
    private List<MessageReader> readers = new ArrayList<>();

    // 읽음 상태 관리를 위한 내부 클래스
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReader {
        private String userId;
        private LocalDateTime readAt;
    }

    // 사용자가 메시지를 읽었는지 확인하는 메서드
    public boolean isReadBy(String userId) {
        return readers.stream()
                .anyMatch(reader -> reader.getUserId().equals(userId));
    }

    // 사용자의 읽음 상태를 추가하는 메서드
    public void markAsReadBy(String userId) {
        if (!isReadBy(userId)) {
            readers.add(MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .build());
        }
    }

    // 메시지를 읽은 사용자 수를 반환하는 메서드
    public int getReadCount() {
        return readers.size();
    }

    private FileMetadata metadata;

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
