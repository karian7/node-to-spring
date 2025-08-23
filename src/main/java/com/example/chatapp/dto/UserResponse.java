package com.example.chatapp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    @JsonProperty("_id")
    private String id;
    private String name;
    private String email;
    private String profileImage;
    private LocalDateTime createdAt;
    private String status; // online, offline, away

    // User 엔티티에서 UserResponse로 변환하는 정적 메서드
    public static UserResponse from(com.example.chatapp.model.User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
