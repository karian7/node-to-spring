package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.MarkAsReadRequest;
import com.example.chatapp.dto.MessagesReadResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 메시지 읽음 상태 처리 핸들러
 * 메시지 읽음 상태 업데이트 및 브로드캐스트 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageReadHandler {
    
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    @OnEvent(MARK_MESSAGES_AS_READ)
    public void handleMarkAsRead(SocketIOClient client, MarkAsReadRequest data) {
        try {
            String userId = getUserId(client);
            if (userId == null) {
                client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            if (data == null || data.getMessageIds() == null || data.getMessageIds().isEmpty()) {
                return;
            }
            
            String roomId = messageRepository.findById(data.getMessageIds().getFirst())
                    .map(Message::getRoomId).orElse(null);
            
            if (roomId == null || roomId.isBlank()) {
                client.sendEvent(ERROR, Map.of("message", "Invalid room"));
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                client.sendEvent(ERROR, Map.of("message", "User not found"));
                return;
            }

            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null || !room.getParticipantIds().contains(userId)) {
                client.sendEvent(ERROR, Map.of("message", "Room access denied"));
                return;
            }

            Message.MessageReader readerInfo = Message.MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .build();

            long modifiedCount = messageRepository.addReaderToMessages(
                    data.getMessageIds(),
                    roomId,
                    userId,
                    readerInfo
            );

            if (modifiedCount == 0) {
                log.debug("No messages marked as read - roomId: {}, userId: {}", roomId, userId);
                return;
            }

            log.debug("Messages marked as read - roomId: {}, userId: {}, modified: {}",
                roomId, userId, modifiedCount);

            MessagesReadResponse response = new MessagesReadResponse(userId, data.getMessageIds());

            // Broadcast to room
            socketIOServer.getRoomOperations(roomId)
                    .sendEvent(MESSAGES_READ, response);

        } catch (Exception e) {
            log.error("Error handling markMessagesAsRead", e);
            client.sendEvent(ERROR, Map.of(
                    "message", "읽음 상태 업데이트 중 오류가 발생했습니다."
            ));
        }
    }
    
    private String getUserId(SocketIOClient client) {
        var user = (UserResponse) client.get("user");
        return user.getId();
    }
}
