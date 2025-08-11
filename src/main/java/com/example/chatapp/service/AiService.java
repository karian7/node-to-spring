package com.example.chatapp.service;

import com.example.chatapp.dto.ai.OpenAiRequest;
import com.example.chatapp.dto.ai.OpenAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class AiService {

    private final WebClient webClient;

    public AiService(WebClient.Builder webClientBuilder, @Value("${openai.api.key}") String apiKey) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Flux<String> generateResponse(String message, String persona) {
        String systemPrompt = getSystemPrompt(persona);

        OpenAiRequest.Message systemMessage = new OpenAiRequest.Message("system", systemPrompt);
        OpenAiRequest.Message userMessage = new OpenAiRequest.Message("user", message);

        OpenAiRequest request = new OpenAiRequest(
                "gpt-4",
                List.of(systemMessage, userMessage),
                0.7,
                true
        );

        return this.webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(OpenAiResponse.class)
                .map(response -> {
                    if (response.choices() != null && !response.choices().isEmpty()) {
                        OpenAiResponse.Delta delta = response.choices().get(0).delta();
                        if (delta != null && delta.content() != null) {
                            return delta.content();
                        }
                    }
                    return "";
                });
    }

    private String getSystemPrompt(String persona) {
        return switch (persona) {
            case "consultingAI" -> "You are Consulting AI. Your role is a business consulting expert. You provide professional advice on business strategy, market analysis, and organizational management in a professional and analytical tone.";
            default -> "You are Wayne AI. Your role is a friendly and helpful assistant. You provide professional and insightful answers, deeply understand user questions, and offer clear explanations in a professional yet friendly tone.";
        };
    }

    // 노드 버전과 호환되는 스트리밍 응답 메서드 추가
    public void generateStreamingResponse(com.example.chatapp.model.AiType aiType, String query,
                                        java.util.function.Consumer<String> onChunk,
                                        Runnable onComplete) {
        try {
            String persona = mapAiTypeToPersona(aiType);
            generateResponse(query, persona)
                .doOnNext(onChunk::accept)
                .doOnComplete(onComplete)
                .subscribe();
        } catch (Exception e) {
            throw new RuntimeException("AI 스트리밍 응답 생성 중 오류가 발생했습니다.", e);
        }
    }

    private String mapAiTypeToPersona(com.example.chatapp.model.AiType aiType) {
        return switch (aiType) {
            case GPT -> "gpt-assistant";
            case CLAUDE -> "claude-assistant";
            case GEMINI -> "gemini-assistant";
            case WAYNE_AI -> "wayneAI";
            case CONSULTING_AI -> "consultingAI";
        };
    }
}
