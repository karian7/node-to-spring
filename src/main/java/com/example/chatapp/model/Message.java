package com.example.chatapp.model;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Message 문서 모델 정의.
 * MongoDB 필드 이름과 인덱스를 명시한다.
 * Node.js Message 모델과 일치하도록 구현.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndexes({
    @CompoundIndex(name = "room_timestamp_idx", def = "{'room': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "room_isDeleted_idx", def = "{'room': 1, 'isDeleted': 1}"),
    @CompoundIndex(name = "readers_userId_idx", def = "{'readers.userId': 1}"),
    @CompoundIndex(name = "reactions_userId_idx", def = "{'reactions': 1}")
})
public class Message {

    @Id
    private String id;

    // Mongo 문서 필드명 "room" 사용
    @Indexed
    @Field("room")
    private String roomId;

    // Node.js 스펙: maxlength 10000
    @Size(max = 10000, message = "메시지는 10000자를 초과할 수 없습니다.")
    private String content;

    // Mongo 문서 필드명 "sender" 사용
    @Indexed
    @Field("sender")
    private String senderId;

    private MessageType type;

    // Mongo 문서 필드명 "file" 사용
    @Field("file")
    private String fileId;

    @Indexed
    private AiType aiType;

    // Node.js 스펙: AI 멘션 저장 (현재 사용처 없지만 스펙 준수)
    @Builder.Default
    private List<String> mentions = new ArrayList<>();

    @Indexed
    @CreatedDate
    private LocalDateTime timestamp;

    @Builder.Default
    private Map<String, Set<String>> reactions = new HashMap<>();

    // 메시지 읽음 상태 관리
    @Builder.Default
    private List<MessageReader> readers = new ArrayList<>();

    // 자유 형식 metadata 저장 필드
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // Node.js 스펙: soft delete 지원
    @Indexed
    @Builder.Default
    private Boolean isDeleted = false;

    // 메시지 읽음 상태를 나타내는 내부 클래스
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageReader {
        private String userId;
        private LocalDateTime readAt;
    }
    
    
    public long toTimestampMillis() {
        return timestamp.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000;
    }
}
