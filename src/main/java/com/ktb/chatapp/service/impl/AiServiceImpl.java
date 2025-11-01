package com.ktb.chatapp.service.impl;

import com.ktb.chatapp.model.AiType;
import com.ktb.chatapp.service.AiService;
import com.ktb.chatapp.websocket.socketio.handler.StreamingSession;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 서비스 구현체
 * Spring AI ChatClient를 사용한 스트리밍 응답 생성
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final String MODEL = "gpt-4.1-mini";
    private static final double TEMPERATURE = 0.7;

    private final ChatClient chatClient;

    public AiServiceImpl(
            ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public Flux<ChunkData> streamResponse(StreamingSession session) {
        return Flux.defer(() -> {
            AiType aiType = session.aiTypeEnum();
            String query = session.getQuery();
            if (aiType == null) {
                return Flux.error(new IllegalArgumentException("Unknown AI persona"));
            }

            Flux<String> contentStream = chatClient.prompt()
                    .system(aiType.getSystemPrompt())
                    .user(query)
                    .stream()
                    .content();

            AtomicBoolean codeBlockState = new AtomicBoolean(false);

            return contentStream
                    .filter(chunk -> chunk != null && !chunk.isBlank())
                    .map(chunk -> new ChunkData(chunk, updateCodeBlockState(chunk, codeBlockState)))
                    .doOnSubscribe(subscription -> log.info(
                            "Starting AI streaming response - aiType: {}, query: {}",
                            aiType, query))
                    .doOnError(error -> log.error("Streaming error received from Spring AI", error));
        });
    }

    private boolean updateCodeBlockState(String chunk, AtomicBoolean codeBlockState) {
        boolean currentState = codeBlockState.get();
        int index = 0;
        while ((index = chunk.indexOf("```", index)) != -1) {
            currentState = !currentState;
            index += 3;
        }
        codeBlockState.set(currentState);
        return currentState;
    }
}
