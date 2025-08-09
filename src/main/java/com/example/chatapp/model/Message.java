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

    private boolean isDeleted = false;
}
