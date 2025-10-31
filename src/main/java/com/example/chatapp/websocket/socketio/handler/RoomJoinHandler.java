package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.ActiveStreamResponse;
import com.example.chatapp.dto.FetchMessagesResponse;
import com.example.chatapp.dto.JoinRoomSuccessResponse;
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
 * 방 입장 처리 핸들러
 * 채팅방 입장, 참가자 관리, 초기 메시지 로드 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomJoinHandler {

    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final UserRoomRegistry userRoomRegistry;
    private final MessageFetchHandler messageFetchHandler;
    private final ChatMessageHandler chatMessageHandler;
    private final MessageResponseMapper messageResponseMapper;
    
    @OnEvent(JOIN_ROOM)
    public void handleJoinRoom(SocketIOClient client, String roomId) {
        try {
            String userId = getUserId(client);
            String userName = getUserName(client);

            if (userId == null) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            // 이미 해당 방에 참여 중인지 확인
            String currentRoom = userRoomRegistry.getRoomIdForUser(userId);
            if (roomId.equals(currentRoom)) {
                log.debug("User {} already in room {}", userId, roomId);
                client.joinRoom(roomId);
                client.sendEvent(JOIN_ROOM_SUCCESS, Map.of("roomId", roomId));
                return;
            }

            // 기존 방에서 나가기
            if (currentRoom != null) {
                log.debug("User {} leaving current room {}", userId, currentRoom);
                client.leaveRoom(currentRoom);
                userRoomRegistry.removeRoomIdForUser(userId);

                // 기존 방에 퇴장 알림
                if (userName != null) {
                    socketIOServer.getRoomOperations(currentRoom)
                        .sendEvent(USER_LEFT, Map.of(
                            "userId", userId,
                            "name", userName
                        ));
                }
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "User not found"));
                return;
            }

            // 채팅방 참가 with profileImage
            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null) {
                client.sendEvent(JOIN_ROOM_ERROR, Map.of("message", "채팅방을 찾을 수 없습니다."));
                return;
            }

            roomRepository.addParticipant(roomId, userId);
            room.addParticipant(userId);

            // Join socket room
            client.joinRoom(roomId);
            userRoomRegistry.saveRoomIdForUser(userId, roomId);

            Message joinMessage = Message.builder()
                .roomId(roomId)
                .content(userName + "님이 입장하였습니다.")
                .type(MessageType.system)
                .timestamp(LocalDateTime.now())
                .mentions(new ArrayList<>())
                .isDeleted(false)
                .reactions(new HashMap<>())
                .readers(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();

            joinMessage = messageRepository.save(joinMessage);

            // 초기 메시지 로드
            FetchMessagesResponse messageLoadResult = messageFetchHandler.loadInitialMessages(roomId, userId);

            // 참가자 정보 조회 (with profileImage)
            List<User> participantUsers = userRepository.findAllById(room.getParticipantIds());
            List<UserResponse> participants = participantUsers.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());

            // 활성 스트리밍 메시지 조회
            List<ActiveStreamResponse> activeStreams = chatMessageHandler.getStreamingSessions().stream()
                .filter(session -> roomId.equals(session.getRoomId()))
                .map(session -> ActiveStreamResponse.builder()
                    .id(session.getMessageId())
                    .type("ai")
                    .aiType(session.getAiType())
                    .content(session.getContent())
                    .timestamp(session.timestampToString())
                    .isStreaming(true)
                    .build())
                .collect(Collectors.toList());

            // joinRoomSuccess 이벤트 발송
            JoinRoomSuccessResponse response = JoinRoomSuccessResponse.builder()
                .roomId(roomId)
                .participants(participants)
                .messages(messageLoadResult.getMessages())
                .hasMore(messageLoadResult.isHasMore())
                .oldestTimestamp(messageLoadResult.getOldestTimestamp())
                .activeStreams(activeStreams)
                .build();

            client.sendEvent(JOIN_ROOM_SUCCESS, response);

            // 입장 메시지 브로드캐스트
            socketIOServer.getRoomOperations(roomId)
                .sendEvent(MESSAGE, messageResponseMapper.mapToMessageResponse(joinMessage, null));

            // 참가자 목록 업데이트 브로드캐스트
            socketIOServer.getRoomOperations(roomId)
                .sendEvent(PARTICIPANTS_UPDATE, participants);

            log.info("User {} joined room {} successfully. Message count: {}, hasMore: {}",
                userName, roomId, messageLoadResult.getMessages().size(), messageLoadResult.isHasMore());

        } catch (Exception e) {
            log.error("Error handling joinRoom", e);
            client.sendEvent(JOIN_ROOM_ERROR, Map.of(
                "message", e.getMessage() != null ? e.getMessage() : "채팅방 입장에 실패했습니다."
            ));
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
