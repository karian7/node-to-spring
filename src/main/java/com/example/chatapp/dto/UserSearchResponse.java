package com.example.chatapp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSearchResponse {
    private boolean success;
    private String message;
    private List<UserSummaryResponse> users;
    private int totalPages;
    private long totalElements;
    private int currentPage;
}
