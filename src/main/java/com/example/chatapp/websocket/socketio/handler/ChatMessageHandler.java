package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.example.chatapp.dto.FileResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.AiService;
import com.example.chatapp.service.SessionService;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.example.chatapp.websocket.socketio.SocketIOEvents.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageHandler {
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final AiService aiService;
    private final SessionService sessionService;
    
    // 스트리밍 세션 관리 맵
    private final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();
    
    @OnEvent(CHAT_MESSAGE)
    public void handleChatMessage(SocketIOClient client, Map<String, Object> data) {
        try {
            UserResponse userResponse = client.get("user");

            if (userResponse == null) {
                client.sendEvent(ERROR, Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "User authentication required"
                ));
                return;
            }

            String userId = userResponse.getId();
            String sessionId = client.get("sessionId");

            if (sessionId == null || sessionId.isBlank()) {
                client.sendEvent(ERROR, Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "세션이 만료되었습니다. 다시 로그인해주세요."
                ));
                return;
            }

            SessionService.SessionValidationResult validation =
                sessionService.validateSession(userId, sessionId);
            if (!validation.isValid()) {
                client.sendEvent(ERROR, Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "세션이 만료되었습니다. 다시 로그인해주세요."
                ));
                return;
            }

            if (data == null) {
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "메시지 데이터가 없습니다."
                ));
                return;
            }

            String roomId = (String) data.get("room");
            String type = (String) data.get("type");
            String content = (String) data.get("content");

            if (content == null || content.trim().isEmpty()) {
                content = (String) data.get("msg");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> fileData = (Map<String, Object>) data.get("fileData");

            if (roomId == null) {
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "채팅방 정보가 없습니다."
                ));
                return;
            }

            User sender = userRepository.findById(userId).orElse(null);
            if (sender == null) {
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "User not found"
                ));
                return;
            }

            Room room = roomRepository.findById(roomId).orElse(null);
            if (room == null || !room.getParticipantIds().contains(userId)) {
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "채팅방 접근 권한이 없습니다."
                ));
                return;
            }

            List<String> aiMentions = extractAIMentions(content);

            log.debug("Message received - type: {}, room: {}, userId: {}, hasFileData: {}, hasAIMentions: {}",
                type, roomId, userId, fileData != null, aiMentions.size());

            Message message = switch (type != null ? type : "text") {
                case "file" -> handleFileMessage(roomId, userId, content, fileData);
                case "text" -> handleTextMessage(roomId, userId, content);
                default -> handleTextMessage(roomId, userId, content);
            };

            if (message == null) {
                return;
            }

            Message savedMessage = messageRepository.save(message);

            if (savedMessage.needsFileMetadata()) {
                fileRepository.findById(savedMessage.getFileId())
                    .ifPresent(savedMessage::attachFileMetadata);
            }

            var roomOperations = socketIOServer.getRoomOperations(roomId);
            roomOperations.sendEvent(MESSAGE, createMessageResponse(savedMessage, sender));

            if (!aiMentions.isEmpty()) {
                for (String aiType : aiMentions) {
                    String query = content.replaceAll("@" + aiType + "\\b", "").trim();
                    handleAIResponse(roomId, userId, aiType, query);
                }
            }

            sessionService.updateLastActivity(userId);

            log.debug("Message processed - messageId: {}, type: {}, room: {}",
                savedMessage.getId(), savedMessage.getType(), roomId);

        } catch (Exception e) {
            log.error("Message handling error", e);
            client.sendEvent(ERROR, Map.of(
                "code", "MESSAGE_ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "메시지 전송 중 오류가 발생했습니다."
            ));
        }
    }

    private Message handleFileMessage(String roomId, String userId, String content, Map<String, Object> fileData) {
        if (fileData == null || fileData.get("_id") == null) {
            throw new RuntimeException("파일 데이터가 올바르지 않습니다.");
        }

        String fileId = (String) fileData.get("_id");
        com.example.chatapp.model.File file = fileRepository.findById(fileId).orElse(null);

        if (file == null || !file.getUser().equals(userId)) {
            throw new RuntimeException("파일을 찾을 수 없거나 접근 권한이 없습니다.");
        }

        String trimmedContent = content != null ? content.trim() : "";
        List<String> mentions = extractAIMentions(trimmedContent);

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setType(MessageType.file);
        message.setFileId(fileId);
        message.setContent(trimmedContent);
        message.setTimestamp(LocalDateTime.now());
        message.setReactions(new java.util.HashMap<>());
        message.setMentions(mentions);
        message.setIsDeleted(false);
        
        // 메타데이터는 Map<String, Object>
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileType", file.getMimetype());
        metadata.put("fileSize", file.getSize());
        metadata.put("originalName", file.getOriginalname());
        message.setMetadata(metadata);

        return message;
    }

    private Message handleTextMessage(String roomId, String userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null; // 빈 메시지는 무시
        }

        String trimmedContent = content.trim();
        List<String> mentions = extractAIMentions(trimmedContent);

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setContent(trimmedContent);
        message.setType(MessageType.text);
        message.setTimestamp(LocalDateTime.now());
        message.setReactions(new java.util.HashMap<>());
        message.setMentions(mentions);
        message.setIsDeleted(false);

        return message;
    }

    // JavaScript 버전과 동일한 AI 멘션 추출
    private List<String> extractAIMentions(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        Pattern mentionPattern = Pattern.compile("@(wayneAI|consultingAI)\\b");
        Matcher matcher = mentionPattern.matcher(content);
        Set<String> mentions = new LinkedHashSet<>();

        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }

        return new ArrayList<>(mentions);
    }

    // JavaScript 버전과 동일한 구조의 응답 생성
    private MessageResponse createMessageResponse(Message message, User sender) {
        var messageResponse = new MessageResponse();
        messageResponse.setId(message.getId());
        messageResponse.setRoomId(message.getRoomId());
        messageResponse.setContent(message.getContent());
        messageResponse.setType(message.getType());
        messageResponse.setTimestamp(message.toTimestampMillis());
        messageResponse.setReactions(message.getReactions() != null ? message.getReactions() : Collections.emptyMap());
        messageResponse.setSender(UserResponse.from(sender));

        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId())
                    .ifPresent(file -> messageResponse.setFile(FileResponse.from(file)));
        }

        messageResponse.setMetadata(message.getMetadata());

        return messageResponse;
    }

    private void handleAIResponse(String roomId, String userId, String aiType, String query) {
        // AI 스트리밍 세션 생성 - messageId는 타입과 타임스탬프 조합
        String messageId = aiType + "-" + System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();

        log.info("AI response started - messageId: {}, room: {}, aiType: {}, query: {}",
            messageId, roomId, aiType, query);

        // 스트리밍 세션 초기화
        StreamingSession session = StreamingSession.builder()
            .messageId(messageId)
            .roomId(roomId)
            .userId(userId)
            .aiType(aiType)
            .content("")
            .timestamp(timestamp)
            .lastUpdate(System.currentTimeMillis())
            .isStreaming(true)
            .build();
        streamingSessions.put(messageId, session);

        // AI 스트리밍 시작 알림 전송
        socketIOServer.getRoomOperations(roomId)
                .sendEvent(AI_MESSAGE_START, Map.of(
                    "messageId", messageId,
                    "aiType", aiType,
                    "timestamp", timestamp
                ));
        
        AiType aiTypeEnum = convertToAiType(aiType);
        AiService.StreamingCallbacks callbackHandler = new AiStreamingCallbackHandler(
                messageId,
                roomId,
                aiTypeEnum,
                query,
                socketIOServer,
                messageRepository,
                streamingSessions
        );
        
        // 실제 AI 서비스 호출 (비동기)
        CompletableFuture.runAsync(() -> {
            aiService.generateStreamingResponse(aiTypeEnum, query, callbackHandler);
        });
    }
    

    // AI 타입 문자열을 AiType enum으로 변환
    private AiType convertToAiType(String aiTypeString) {
        if (aiTypeString == null) return null;

        var aiType = switch (aiTypeString.toLowerCase()) {
            case "wayneai" -> AiType.WAYNE_AI; // wayneAI는 GPT로 매핑
            case "consultingai" -> AiType.CONSULTING_AI; // consultingAI는 Claude로 매핑
            default -> null;
        };
        if (aiType == null) {
            log.warn("Unknown AI type: {}", aiTypeString);
        }
        return aiType;
    }
    
    public Collection<StreamingSession> getStreamingSessions() {
        return streamingSessions.values();
    }
    
    public boolean terminateStreamingSessions(String roomId, String userId) {
        return streamingSessions.entrySet().removeIf(entry -> {
            StreamingSession session = entry.getValue();
            return roomId.equals(session.getRoomId()) && userId.equals(session.getUserId());
        });
    }
}
