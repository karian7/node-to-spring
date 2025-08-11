package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    @Transactional
    public DataListener<Map> getListener() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");
                String userName = client.getHandshakeData().getHttpHeaders().get("socket.user.name");

                if (userId == null) {
                    client.sendEvent("error", Map.of(
                        "code", "UNAUTHORIZED",
                        "message", "User authentication required"
                    ));
                    return;
                }

                // 데이터 검증
                if (data == null) {
                    client.sendEvent("error", Map.of(
                        "code", "MESSAGE_ERROR",
                        "message", "메시지 데이터가 없습니다."
                    ));
                    return;
                }

                String roomId = (String) data.get("room");
                String type = (String) data.get("type");
                String content = (String) data.get("content");

                // msg 필드도 확인 (JavaScript 버전에서 사용)
                if (content == null || content.trim().isEmpty()) {
                    content = (String) data.get("msg");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> fileData = (Map<String, Object>) data.get("fileData");

                if (roomId == null) {
                    client.sendEvent("error", Map.of(
                        "code", "MESSAGE_ERROR",
                        "message", "채팅방 정보가 없습니다."
                    ));
                    return;
                }

                // 사용자 조회
                User sender = userRepository.findById(userId).orElse(null);
                if (sender == null) {
                    client.sendEvent("error", Map.of(
                        "code", "MESSAGE_ERROR",
                        "message", "User not found"
                    ));
                    return;
                }

                // 채팅방 권한 확인
                Room room = roomRepository.findById(roomId).orElse(null);
                if (room == null || !room.getParticipantIds().contains(userId)) {
                    client.sendEvent("error", Map.of(
                        "code", "MESSAGE_ERROR",
                        "message", "채팅방 접근 권한이 없습니다."
                    ));
                    return;
                }

                // AI 멘션 확인
                List<String> aiMentions = extractAIMentions(content);
                Message message;

                log.debug("Message received - type: {}, room: {}, userId: {}, hasFileData: {}, hasAIMentions: {}",
                    type, roomId, userId, fileData != null, aiMentions.size());

                // 메시지 타입별 처리 (JavaScript 버전과 동일한 switch 구조)
                switch (type != null ? type : "text") {
                    case "file":
                        message = handleFileMessage(roomId, userId, content, fileData);
                        break;
                    case "text":
                    default:
                        message = handleTextMessage(roomId, userId, content);
                        break;
                }

                if (message == null) {
                    return; // 에러는 각 핸들러에서 처리
                }

                // 메시지 저장
                Message savedMessage = messageRepository.save(message);

                // sender와 file 정보 populate (JavaScript 버전과 동일)
                if (savedMessage.getFileId() != null) {
                    com.example.chatapp.model.File file = fileRepository.findById(savedMessage.getFileId()).orElse(null);
                    if (file != null) {
                        savedMessage.setMetadata(Message.FileMetadata.builder()
                            .fileType(file.getMimetype())
                            .fileSize(file.getSize())
                            .originalName(file.getOriginalname())
                            .build());
                    }
                }

                // JavaScript 버전과 동일한 구조로 브로드캐스트
                Map<String, Object> messageResponse = createMessageResponse(savedMessage, sender);
                socketIOServer.getRoomOperations("room:" + roomId)
                        .sendEvent("message", messageResponse);

                // AI 멘션이 있는 경우 AI 응답 생성 (JavaScript 버전과 동일)
                if (!aiMentions.isEmpty()) {
                    for (String aiType : aiMentions) {
                        String query = content.replaceAll("@" + aiType + "\\b", "").trim();
                        handleAIResponse(roomId, aiType, query);
                    }
                }

                log.debug("Message processed - messageId: {}, type: {}, room: {}",
                    savedMessage.getId(), savedMessage.getType(), roomId);

            } catch (Exception e) {
                log.error("Message handling error", e);
                client.sendEvent("error", Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", e.getMessage() != null ? e.getMessage() : "메시지 전송 중 오류가 발생했습니다."
                ));
            }
        };
    }

    private Message handleFileMessage(String roomId, String userId, String content, Map<String, Object> fileData) {
        if (fileData == null || fileData.get("_id") == null) {
            throw new RuntimeException("파일 데이터가 올바르지 않습니다.");
        }

        String fileId = (String) fileData.get("_id");
        com.example.chatapp.model.File file = fileRepository.findById(fileId).orElse(null);

        if (file == null || !file.getUploadedBy().equals(userId)) {
            throw new RuntimeException("파일을 찾을 수 없거나 접근 권한이 없습니다.");
        }

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setType(MessageType.FILE);
        message.setFileId(fileId);
        message.setContent(content != null ? content : "");
        message.setTimestamp(LocalDateTime.now());
        message.setReactions(new java.util.HashMap<>());
        message.setMetadata(Message.FileMetadata.builder()
                .fileType(file.getMimetype())
                .fileSize(file.getSize())
                .originalName(file.getOriginalname())
                .build());

        return message;
    }

    private Message handleTextMessage(String roomId, String userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return null; // 빈 메시지는 무시
        }

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setContent(content.trim());
        message.setType(MessageType.TEXT);
        message.setTimestamp(LocalDateTime.now());
        message.setReactions(new java.util.HashMap<>());

        return message;
    }

    // JavaScript 버전과 동일한 AI 멘션 추출
    private List<String> extractAIMentions(String content) {
        List<String> mentions = new java.util.ArrayList<>();
        if (content == null) return mentions;

        // JavaScript 버전과 동일한 AI 타입들
        String[] aiTypes = {"wayneAI", "consultingAI", "gpt", "claude", "gemini"};

        for (String aiType : aiTypes) {
            if (content.contains("@" + aiType)) {
                mentions.add(aiType);
            }
        }

        return mentions;
    }

    // JavaScript 버전과 동일한 구조의 응답 생성
    private Map<String, Object> createMessageResponse(Message message, User sender) {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("_id", message.getId());
        response.put("room", message.getRoomId());
        response.put("content", message.getContent());
        response.put("type", message.getType().toString().toLowerCase());
        response.put("timestamp", message.getTimestamp());
        response.put("reactions", message.getReactions() != null ? message.getReactions() : new java.util.HashMap<>());

        // sender 정보 추가
        if (sender != null) {
            Map<String, Object> senderInfo = new java.util.HashMap<>();
            senderInfo.put("_id", sender.getId());
            senderInfo.put("name", sender.getName());
            senderInfo.put("email", sender.getEmail());
            senderInfo.put("profileImage", sender.getProfileImage());
            response.put("sender", senderInfo);
        }

        // file 정보 추가
        if (message.getFileId() != null) {
            com.example.chatapp.model.File file = fileRepository.findById(message.getFileId()).orElse(null);
            if (file != null) {
                Map<String, Object> fileInfo = new java.util.HashMap<>();
                fileInfo.put("_id", file.getId());
                fileInfo.put("filename", file.getFilename());
                fileInfo.put("originalname", file.getOriginalname());
                fileInfo.put("mimetype", file.getMimetype());
                fileInfo.put("size", file.getSize());
                response.put("file", fileInfo);
            }
        }

        // metadata 추가
        if (message.getMetadata() != null) {
            response.put("metadata", message.getMetadata());
        }

        return response;
    }

    private void handleAIResponse(String roomId, String aiType, String query) {
        // AI 스트리밍 세션 생성
        String sessionId = java.util.UUID.randomUUID().toString();

        log.info("AI response requested - room: {}, aiType: {}, query: {}, sessionId: {}",
            roomId, aiType, query, sessionId);

        // AI 스트리밍 시작 알림 (JavaScript 버전과 동일)
        socketIOServer.getRoomOperations("room:" + roomId)
                .sendEvent("ai.stream.start", Map.of(
                    "sessionId", sessionId,
                    "aiType", aiType,
                    "timestamp", LocalDateTime.now()
                ));

        // 실제 AI 서비스 호출 (비동기)
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // AI 타입에 따른 적절한 AiType enum 변환
                AiType aiTypeEnum = convertToAiType(aiType);

                if (aiTypeEnum == null) {
                    log.warn("Unknown AI type: {}", aiType);
                    socketIOServer.getRoomOperations("room:" + roomId)
                            .sendEvent("ai.stream.error", Map.of(
                                "sessionId", sessionId,
                                "error", "지원하지 않는 AI 타입입니다: " + aiType,
                                "timestamp", LocalDateTime.now()
                            ));
                    return;
                }

                // 스트리밍 응답 생성
                aiService.generateStreamingResponse(aiTypeEnum, query,
                    // 스트리밍 청크 콜백
                    chunk -> {
                        socketIOServer.getRoomOperations("room:" + roomId)
                                .sendEvent("ai.stream.chunk", Map.of(
                                    "sessionId", sessionId,
                                    "content", chunk,
                                    "timestamp", LocalDateTime.now()
                                ));
                    },
                    // 스트리밍 완료 콜백
                    () -> {
                        socketIOServer.getRoomOperations("room:" + roomId)
                                .sendEvent("ai.stream.complete", Map.of(
                                    "sessionId", sessionId,
                                    "timestamp", LocalDateTime.now()
                                ));
                        log.debug("AI streaming completed for session: {}", sessionId);
                    }
                );

            } catch (Exception e) {
                log.error("AI streaming error for session: {}", sessionId, e);
                socketIOServer.getRoomOperations("room:" + roomId)
                        .sendEvent("ai.stream.error", Map.of(
                            "sessionId", sessionId,
                            "error", e.getMessage() != null ? e.getMessage() : "AI 응답 생성 중 오류가 발생했습니다.",
                            "timestamp", LocalDateTime.now()
                        ));
            }
        });
    }

    // AI 타입 문자열을 AiType enum으로 변환
    private com.example.chatapp.model.AiType convertToAiType(String aiTypeString) {
        if (aiTypeString == null) return null;

        return switch (aiTypeString.toLowerCase()) {
            case "wayneai" -> com.example.chatapp.model.AiType.GPT; // wayneAI는 GPT로 매핑
            case "consultingai" -> com.example.chatapp.model.AiType.CLAUDE; // consultingAI는 Claude로 매핑
            case "gpt" -> com.example.chatapp.model.AiType.GPT;
            case "claude" -> com.example.chatapp.model.AiType.CLAUDE;
            case "gemini" -> com.example.chatapp.model.AiType.GEMINI;
            default -> null;
        };
    }
}
