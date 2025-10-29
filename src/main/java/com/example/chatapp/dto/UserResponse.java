package com.example.chatapp.dto;

import com.example.chatapp.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String profileImage;
    private LocalDateTime createdAt;
    private LocalDateTime lastLogin;
    private boolean isOnline;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .profileImage(user.getProfileImage())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .isOnline(user.isOnline())
                .build();
    }
}
