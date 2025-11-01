package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.MessageReactionRequest;
import com.example.chatapp.dto.MessageReactionResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 메시지 리액션 처리 핸들러
 * 메시지 이모지 리액션 추가/제거 및 브로드캐스트 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageReactionHandler {
    
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    
    @OnEvent(MESSAGE_REACTION)
    public void handleMessageReaction(SocketIOClient client, MessageReactionRequest data) {
        try {
            String userId = getUserId(client);
            if (userId == null || userId.isBlank()) {
                client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            Message message = messageRepository.findById(data.getMessageId()).orElse(null);
            if (message == null) {
                client.sendEvent(ERROR, Map.of("message", "메시지를 찾을 수 없습니다."));
                return;
            }

            switch (data.getType()) {
                case "add" -> message.addReaction(data.getReaction(), userId);
                case "remove" -> message.removeReaction(data.getReaction(), userId);
                case null, default -> {
                    client.sendEvent(ERROR, Map.of("message", "지원하지 않는 리액션 타입입니다."));
                    return;
                }
            }

            log.debug("Message reaction processed - type: {}, reaction: {}, messageId: {}, userId: {}",
                data.getType(), data.getReaction(), message.getId(), userId);

            messageRepository.save(message);

            MessageReactionResponse response = new MessageReactionResponse(
                message.getId(),
                message.getReactions()
            );

            socketIOServer.getRoomOperations(message.getRoomId())
                .sendEvent(MESSAGE_REACTION_UPDATE, response);

        } catch (Exception e) {
            log.error("Error handling messageReaction", e);
            client.sendEvent(ERROR, Map.of(
                "message", "리액션 처리 중 오류가 발생했습니다."
            ));
        }
    }
    
    private String getUserId(SocketIOClient client) {
        var user = (UserResponse) client.get("user");
        return user.getId();
    }
}
