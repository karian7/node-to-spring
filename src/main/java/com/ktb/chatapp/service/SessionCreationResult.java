package com.ktb.chatapp.service;

import com.ktb.chatapp.service.SessionService.SessionData;
import lombok.*;

@Builder
@Data
public class SessionCreationResult {
    private String sessionId;
    private long expiresIn;
    private SessionData sessionData;
}
