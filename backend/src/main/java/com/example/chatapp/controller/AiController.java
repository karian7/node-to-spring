package com.example.chatapp.controller;

import com.example.chatapp.dto.ai.AiRequest;
import com.example.chatapp.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithAi(@RequestBody AiRequest aiRequest) {
        return aiService.generateResponse(aiRequest.getMessage(), aiRequest.getPersona());
    }
}
