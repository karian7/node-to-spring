package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.model.AiType;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.service.AiService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

@Slf4j
@RequiredArgsConstructor
public class AiStreamHandler implements Subscriber<AiService.ChunkData> {
    private final StreamingSession session;
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private Subscription subscription;

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AiService.ChunkData chunk) {
        session.appendContent(chunk.currentChunk());
        
        String messageId = session.getMessageId();
        String roomId = session.getRoomId();
        AiType aiType = session.aiTypeEnum();
        if (roomId == null) {
            log.warn("Room id missing while processing AI chunk - messageId: {}", messageId);
            return;
        }

        socketIOServer.getRoomOperations(roomId)
            .sendEvent(AI_MESSAGE_CHUNK, Map.of(
                "messageId", messageId,
                "currentChunk", chunk.currentChunk(),
                "fullContent", session.getContent(),
                "isCodeBlock", chunk.codeBlock(),
                "timestamp", LocalDateTime.now(),
                "aiType", aiType,
                "isComplete", false
            ));
    }

    @Override
    public void onError(Throwable error) {
        String messageId = session.getMessageId();
        log.error("AI streaming error for messageId: {}", messageId, error);

        String errorMessage = error.getMessage() != null
            ? error.getMessage()
            : "AI 응답 생성 중 오류가 발생했습니다.";
        sendErrorEvent(errorMessage);
    }

    @Override
    public void onComplete() {
        String messageId = session.getMessageId();

        try {
            Message aiMessage = createAiMessage();
            Message savedMessage = messageRepository.save(aiMessage);
            sendCompletionEvent(savedMessage);
            log.debug("AI streaming completed for messageId: {}", messageId);
        } catch (Exception e) {
            log.error("Error saving AI message for messageId: {}", messageId, e);
            sendErrorEvent("AI 메시지 저장 중 오류가 발생했습니다.");
        }
    }

    public boolean matches(String roomId, String userId) {
        return Objects.equals(roomId, session.getRoomId())
            && Objects.equals(userId, session.getUserId());
    }

    public void cancel() {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    private Message createAiMessage() {
        Message aiMessage = new Message();
        aiMessage.setRoomId(session.getRoomId());
        aiMessage.setContent(session.getContent());
        aiMessage.setType(MessageType.ai);
        aiMessage.setAiType(session.aiTypeEnum());
        aiMessage.setTimestamp(LocalDateTime.now());
        aiMessage.setReactions(new HashMap<>());
        aiMessage.setMentions(new ArrayList<>());
        aiMessage.setIsDeleted(false);
        aiMessage.setReaders(new ArrayList<>());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("query", session.getQuery());
        metadata.put("generationTime", session.getStartTimeMillis());
        aiMessage.setMetadata(metadata);

        return aiMessage;
    }

    private void sendCompletionEvent(Message savedMessage) {
        socketIOServer.getRoomOperations(session.getRoomId())
            .sendEvent(AI_MESSAGE_COMPLETE, Map.of(
                "messageId", session.getMessageId(),
                "_id", savedMessage.getId(),
                "content", session.getContent(),
                "aiType", session.aiTypeEnum(),
                "timestamp", LocalDateTime.now(),
                "isComplete", true,
                "query", session.getQuery(),
                "reactions", new HashMap<>()
            ));
    }

    private void sendErrorEvent(String errorMessage) {
        socketIOServer.getRoomOperations(session.getRoomId())
            .sendEvent(AI_MESSAGE_ERROR, Map.of(
                "messageId", session.getMessageId(),
                "error", errorMessage,
                "aiType", session.aiTypeEnum()
            ));
    }
}
