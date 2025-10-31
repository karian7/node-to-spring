package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.MessageType;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.websocket.socketio.UserRoomRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 방 퇴장 처리 핸들러
 * 채팅방 퇴장, 스트리밍 세션 종료, 참가자 목록 업데이트 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomLeaveHandler {

    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserRoomRegistry userRoomRegistry;
    private final ChatMessageHandler chatMessageHandler;
    private final MessageResponseMapper messageResponseMapper;
    
    @OnEvent(LEAVE_ROOM)
    public void handleLeaveRoom(SocketIOClient client, String roomId) {
        try {
            String userId = getUserId(client);
            String userName = getUserName(client);

            if (userId == null) {
                client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            String currentRoom = userRoomRegistry.getRoomIdForUser(userId);
            if (currentRoom == null || !currentRoom.equals(roomId)) {
                log.debug("User {} is not in room {}", userId, roomId);
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            Room room = roomRepository.findById(roomId).orElse(null);
            
            if (user == null || room == null) {
                log.warn("Room {} not found or user {} has no access", roomId, userId);
                return;
            }
            room.removeParticipant(userId);
            roomRepository.save(room);
            
            client.leaveRoom(roomId);
            userRoomRegistry.removeRoomIdForUser(userId);
            
            log.info("User {} left room {}", userName, room.getName());
            
            boolean removedStreams = chatMessageHandler.terminateStreamingSessions(roomId, userId);
            
            log.debug("Leave room cleanup - roomId: {}, userId: {}, streamsCleared: {}",
                roomId, userId, removedStreams);
            
            sendSystemMessage(roomId, userName + "님이 퇴장하였습니다.");
            broadcastParticipantList(roomId);
            
            // 빈 방 정리
            if (room.isEmpty()) {
                log.info("Room {} is now empty, deleting room", roomId);
                roomRepository.deleteById(roomId);
            }
            
        } catch (Exception e) {
            log.error("Error handling leaveRoom", e);
            client.sendEvent(ERROR, Map.of("message", "채팅방 퇴장 중 오류가 발생했습니다."));
        }
    }

    private void sendSystemMessage(String roomId, String content) {
        try {
            Message systemMessage = new Message();
            systemMessage.setRoomId(roomId);
            systemMessage.setContent(content);
            systemMessage.setType(MessageType.system);
            systemMessage.setTimestamp(LocalDateTime.now());
            systemMessage.setMentions(new ArrayList<>());
            systemMessage.setIsDeleted(false);
            systemMessage.setReactions(new HashMap<>());
            systemMessage.setReaders(new ArrayList<>());
            systemMessage.setMetadata(new HashMap<>());

            Message savedMessage = messageRepository.save(systemMessage);
            MessageResponse response = messageResponseMapper.mapToMessageResponse(savedMessage, null);

            socketIOServer.getRoomOperations(roomId)
                    .sendEvent(MESSAGE, response);

        } catch (Exception e) {
            log.error("Error sending system message", e);
        }
    }

    private void broadcastParticipantList(String roomId) {
        try {
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room != null) {
                List<User> participants = userRepository.findAllById(room.getParticipantIds());

                var participantList = participants.stream()
                        .map(user -> Map.of(
                            "id", user.getId(),
                            "name", user.getName(),
                            "email", user.getEmail(),
                            "profileImage", user.getProfileImage() != null ? user.getProfileImage() : ""
                        ))
                        .collect(Collectors.toList());

                socketIOServer.getRoomOperations(roomId)
                        .sendEvent(PARTICIPANTS_UPDATE, participantList);
            }
        } catch (Exception e) {
            log.error("Error broadcasting participant list", e);
        }
    }

    private UserResponse getUserDto(SocketIOClient client) {
        return client.get("user");
    }

    private String getUserId(SocketIOClient client) {
        UserResponse user = getUserDto(client);
        return user != null ? user.getId() : null;
    }

    private String getUserName(SocketIOClient client) {
        UserResponse user = getUserDto(client);
        return user != null ? user.getName() : null;
    }
}
