package com.ktb.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RoomResponse {
    @JsonProperty("_id")
    private String id;
    private String name;
    private boolean hasPassword;
    private UserResponse creator;
    private List<UserResponse> participants;
    
    @JsonIgnore
    private LocalDateTime createdAtDateTime;
    
    private boolean isCreator;
    
    @JsonGetter("participantsCount")
    public int getParticipantsCount() {
        return participants != null ? participants.size() : 0;
    }
    
    @JsonGetter("createdAt")
    public String getCreatedAt() {
        return createdAtDateTime
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toString();
    }
}
