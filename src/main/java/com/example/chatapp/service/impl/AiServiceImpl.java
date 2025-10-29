package com.example.chatapp.service.impl;

import com.example.chatapp.model.AiType;
import com.example.chatapp.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * AI 서비스 구현체
 * OpenAI GPT-4를 사용한 스트리밍 응답 생성
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Value("${openai.api.key}")
    private String openaiApiKey;

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4";
    private static final double TEMPERATURE = 0.7;
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean isAvailable() {
        // OpenAI API 키가 설정되어 있는지 확인
        return openaiApiKey != null && !openaiApiKey.isBlank() && !"your-openai-api-key".equals(openaiApiKey);
    }

    @Override
    public void generateStreamingResponse(AiType aiType, String query, StreamingCallbacks callbacks) {
        try {
            log.info("Starting AI streaming response - aiType: {}, query: {}", aiType, query);

            // onStart 콜백 호출
            callbacks.onStart();

            // 페르소나별 시스템 프롬프트 생성
            String systemPrompt = aiType.getSystemPrompt();

            // OpenAI API 요청 페이로드 생성
            String requestBody = String.format("""
                {
                    "model": "%s",
                    "messages": [
                        {"role": "system", "content": %s},
                        {"role": "user", "content": %s}
                    ],
                    "temperature": %.1f,
                    "stream": true
                }""",
                MODEL,
                objectMapper.writeValueAsString(systemPrompt),
                objectMapper.writeValueAsString(query),
                TEMPERATURE
            );

            // HTTP 연결 설정
            URL url = new URL(OPENAI_API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + openaiApiKey);
            connection.setDoOutput(true);

            // 요청 전송
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 스트리밍 응답 처리
            StringBuilder fullResponse = new StringBuilder();
            boolean[] isCodeBlock = {false}; // 코드 블록 상태 추적
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // SSE 형식 파싱
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        
                        // 스트림 종료 감지
                        if ("[DONE]".equals(data)) {
                            log.debug("Streaming completed");
                            
                            // onComplete 콜백 호출
                            // Note: OpenAI streaming doesn't provide token counts in stream
                            // They would need to be fetched separately if required
                            callbacks.onComplete(new CompletionData(
                                fullResponse.toString().trim(),
                                null,  // completionTokens - not available in streaming
                                null   // totalTokens - not available in streaming
                            ));
                            break;
                        }

                        try {
                            // JSON 파싱하여 content 추출
                            JsonNode jsonNode = objectMapper.readTree(data);
                            JsonNode choices = jsonNode.get("choices");
                            
                            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null && delta.has("content")) {
                                    String content = delta.get("content").asText();
                                    
                                    if (content != null && !content.isEmpty()) {
                                        // 코드 블록 감지
                                        if (content.contains("```")) {
                                            isCodeBlock[0] = !isCodeBlock[0];
                                        }
                                        
                                        // onChunk 콜백 호출
                                        callbacks.onChunk(new ChunkData(content, isCodeBlock[0]));
                                        
                                        // 전체 응답 누적
                                        fullResponse.append(content);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse streaming chunk: {}", e.getMessage());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error during AI streaming response generation", e);
            // onError 콜백 호출
            callbacks.onError(e);
        }
    }


}
