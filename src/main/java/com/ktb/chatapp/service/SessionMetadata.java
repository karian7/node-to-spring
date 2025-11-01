package com.ktb.chatapp.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SessionMetadata {
    private final String userAgent;
    private final String ipAddress;
    private final String deviceInfo;
}
