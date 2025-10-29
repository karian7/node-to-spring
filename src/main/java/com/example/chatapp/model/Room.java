package com.example.chatapp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rooms")
public class Room {

    @Id
    private String id;

    private String name;

    private String creator;

    private boolean hasPassword;

    @JsonIgnore
    private String password;

    @CreatedDate
    private LocalDateTime createdAt;

    @Field("participantIds")
    @Builder.Default
    private Set<String> participantIds = new HashSet<>();
}
