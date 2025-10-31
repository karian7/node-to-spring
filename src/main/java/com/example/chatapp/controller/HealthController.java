package com.example.chatapp.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final Environment environment;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("timestamp", Instant.now().toString());
        body.put("env", resolveEnvironment());
        return ResponseEntity.ok(body);
    }

    private String resolveEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return activeProfiles[0];
        }

        String env = environment.getProperty("spring.profiles.active");
        if (env == null || env.isBlank()) {
            env = System.getenv().getOrDefault("NODE_ENV",
                    environment.getProperty("spring.profiles.default", "development"));
        }
        return env;
    }
}
