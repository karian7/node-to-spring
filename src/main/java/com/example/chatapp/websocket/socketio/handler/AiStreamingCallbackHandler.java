package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.chatapp.model.AiType;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.MessageType;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.service.AiService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * AI 스트리밍 응답을 처리하는 콜백 핸들러
 * StreamingCallbacks 인터페이스의 구현체로서,
 * 스트리밍 이벤트를 SocketIO로 전송하고 완료 시 메시지를 저장합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class AiStreamingCallbackHandler implements AiService.StreamingCallbacks {
    private final String messageId;
    private final String roomId;
    private final AiType aiType;
    private final String query;
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final Map<String, StreamingSession> streamingSessions;
    private final long startTime = System.currentTimeMillis();
    private final StringBuilder accumulatedContent = new StringBuilder();


    @Override
    public void onStart() {
        if (aiType == null) {
            throw new IllegalArgumentException("Unknown AI persona");
        }
        log.debug("AI generation started - messageId: {}, aiType: {}", messageId, aiType);
    }

    @Override
    public void onChunk(AiService.ChunkData chunk) {
        accumulatedContent.append(chunk.getCurrentChunk());
        
        // 세션 업데이트
        StreamingSession session = streamingSessions.get(messageId);
        if (session != null) {
            session.setContent(accumulatedContent.toString());
            session.setLastUpdate(System.currentTimeMillis());
        }
        
        // 스트리밍 이벤트 전송
        socketIOServer.getRoomOperations(roomId)
                .sendEvent(AI_MESSAGE_CHUNK, Map.of(
                    "messageId", messageId,
                    "currentChunk", chunk.getCurrentChunk(),
                    "fullContent", accumulatedContent.toString(),
                    "isCodeBlock", chunk.isCodeBlock(),
                    "timestamp", LocalDateTime.now(),
                    "aiType", aiType,
                    "isComplete", false
                ));
    }

    @Override
    public void onComplete(AiService.CompletionData completion) {
        try {
            // 스트리밍 세션 정리
            streamingSessions.remove(messageId);
            
            long generationTime = System.currentTimeMillis() - startTime;
            
            // AI 메시지 생성 및 저장
            Message aiMessage = createAiMessage(completion, generationTime);
            Message savedMessage = messageRepository.save(aiMessage);

            // 완료 이벤트 전송
            sendCompletionEvent(savedMessage, completion);

            log.debug("AI streaming completed for messageId: {}", messageId);
        } catch (Exception e) {
            log.error("Error saving AI message for messageId: {}", messageId, e);
            streamingSessions.remove(messageId);
            sendErrorEvent("AI 메시지 저장 중 오류가 발생했습니다.");
        }
    }

    @Override
    public void onError(Exception error) {
        log.error("AI streaming error for messageId: {}", messageId, error);
        streamingSessions.remove(messageId);
        String errorMessage = error.getMessage() != null ?
            error.getMessage() : "AI 응답 생성 중 오류가 발생했습니다.";
        sendErrorEvent(errorMessage);
    }

    private Message createAiMessage(AiService.CompletionData completion, long generationTime) {
        Message aiMessage = new Message();
        aiMessage.setRoomId(roomId);
        aiMessage.setContent(completion.getContent());
        aiMessage.setType(MessageType.ai);
        aiMessage.setAiType(aiType);
        aiMessage.setTimestamp(LocalDateTime.now());
        aiMessage.setReactions(new HashMap<>());
        aiMessage.setMentions(new ArrayList<>());
        aiMessage.setIsDeleted(false);
        aiMessage.setReaders(new ArrayList<>());

        // 메타데이터 설정
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", query);
        metadata.put("generationTime", generationTime);
        if (completion.getCompletionTokens() != null) {
            metadata.put("completionTokens", completion.getCompletionTokens());
        }
        if (completion.getTotalTokens() != null) {
            metadata.put("totalTokens", completion.getTotalTokens());
        }
        aiMessage.setMetadata(metadata);

        return aiMessage;
    }

    private void sendCompletionEvent(Message savedMessage, AiService.CompletionData completion) {
        socketIOServer.getRoomOperations(roomId)
                .sendEvent(AI_MESSAGE_COMPLETE, Map.of(
                    "messageId", messageId,
                    "_id", savedMessage.getId(),
                    "content", completion.getContent(),
                    "aiType", aiType,
                    "timestamp", LocalDateTime.now(),
                    "isComplete", true,
                    "query", query,
                    "reactions", new HashMap<>()
                ));
    }

    private void sendErrorEvent(String errorMessage) {
        socketIOServer.getRoomOperations(roomId)
                .sendEvent(AI_MESSAGE_ERROR, Map.of(
                    "messageId", messageId,
                    "error", errorMessage,
                    "aiType", aiType
                ));
    }
}

