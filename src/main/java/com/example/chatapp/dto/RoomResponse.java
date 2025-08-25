package com.example.chatapp.dto;

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
    private int participantsCount;
    private LocalDateTime createdAt;
    private boolean isCreator;
}
