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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "files")
public class File {

    @Id
    private String id;

    private String filename;

    private String originalname;

    private String mimetype;

    private long size;

    @Field("uploadedBy")
    private String uploadedBy;

    @Field("roomId")
    private String roomId;

    @Field("ragProcessed")
    private boolean ragProcessed = false;

    @CreatedDate
    private LocalDateTime uploadedAt;
}
