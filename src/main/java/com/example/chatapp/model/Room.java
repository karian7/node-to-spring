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
    
    /**
     * 방에 참가자를 추가한다.
     * Tell, Don't Ask 원칙을 준수하여 도메인 로직을 캡슐화한다.
     *
     * @param userId 추가할 사용자 ID
     */
    public void addParticipant(String userId) {
        if (this.participantIds == null) {
            this.participantIds = new HashSet<>();
        }
        this.participantIds.add(userId);
    }
    
    /**
     * 방에서 참가자를 제거한다.
     *
     * @param userId 제거할 사용자 ID
     */
    public void removeParticipant(String userId) {
        if (this.participantIds != null) {
            this.participantIds.remove(userId);
        }
    }
    
    /**
     * 방이 비어있는지 확인한다.
     *
     * @return 참가자가 없으면 true
     */
    public boolean isEmpty() {
        return this.participantIds == null || this.participantIds.isEmpty();
    }
    
    /**
     * 방의 참가자 수를 반환한다.
     *
     * @return 참가자 수
     */
    public int getParticipantCount() {
        return this.participantIds != null ? this.participantIds.size() : 0;
    }
}
