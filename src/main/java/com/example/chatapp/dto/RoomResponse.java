package com.example.chatapp.dto;

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
    private Long id;
    private String name;
    private boolean hasPassword;
    private UserSummaryResponse creator;
    private List<UserSummaryResponse> participants;
    private int participantsCount;
    private LocalDateTime createdAt;
    private boolean isCreator;
}
