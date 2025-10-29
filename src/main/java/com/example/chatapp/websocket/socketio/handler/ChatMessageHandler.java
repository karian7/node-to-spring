package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.DataListener;
import com.example.chatapp.dto.FileResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.AiService;
import com.example.chatapp.websocket.socketio.StreamingSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Node.js와 동일한 스트리밍 세션 관리
    private final Map<String, StreamingSession> streamingSessions = new ConcurrentHashMap<>();

    @Transactional
    @SuppressWarnings("rawtypes")
    public DataListener<Map> getListener() {
        return (client, data, ackSender) -> {
            try {
                String userId = client.getHandshakeData().getHttpHeaders().get("socket.user.id");

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
                message = switch (type != null ? type : "text") {
                    case "file" -> handleFileMessage(roomId, userId, content, fileData);
                    case "text" -> handleTextMessage(roomId, userId, content);
                    default -> handleTextMessage(roomId, userId, content);
                };

                if (message == null) {
                    return; // 에러는 각 핸들러에서 처리
                }

                // 메시지 저장
                Message savedMessage = messageRepository.save(message);

                // sender와 file 정보 populate (JavaScript 버전과 동일)
                if (savedMessage.getFileId() != null) {
                    fileRepository.findById(savedMessage.getFileId()).ifPresent(file -> {
                        savedMessage.setMetadata(Message.FileMetadata.builder()
                            .fileType(file.getMimetype())
                            .fileSize(file.getSize())
                            .originalName(file.getOriginalname())
                            .build());
                    });
                }

                // JavaScript 버전과 동일한 구조로 브로드캐스트
                var roomOperations = socketIOServer.getRoomOperations("room:" + roomId);
                roomOperations.sendEvent("message", createMessageResponse(savedMessage, sender));

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
        String[] aiTypes = {"wayneAI", "consultingAI"};

        for (String aiType : aiTypes) {
            if (content.contains("@" + aiType)) {
                mentions.add(aiType);
            }
        }

        return mentions;
    }

    // JavaScript 버전과 동일한 구조의 응답 생성
    private MessageResponse createMessageResponse(Message message, User sender) {
        var messageResponse = new MessageResponse();
        messageResponse.setId(message.getId());
        messageResponse.setRoomId(message.getRoomId());
        messageResponse.setContent(message.getContent());
        messageResponse.setType(message.getType());
        messageResponse.setTimestamp(message.getTimestamp());
        messageResponse.setReactions(message.getReactions() != null ? message.getReactions() : Collections.emptyMap());
        messageResponse.setSender(UserResponse.from(sender));

        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId())
                    .ifPresent(file -> messageResponse.setFile(FileResponse.from(file)));
        }

        messageResponse.setMetadata(message.getMetadata());

        return messageResponse;
    }

    private void handleAIResponse(String roomId, String aiType, String query) {
        // AI 스트리밍 세션 생성 - Node.js 버전과 동일한 messageId 형식
        String messageId = aiType + "-" + System.currentTimeMillis();
        LocalDateTime timestamp = LocalDateTime.now();

        log.info("AI response started - messageId: {}, room: {}, aiType: {}, query: {}",
            messageId, roomId, aiType, query);

        // 스트리밍 세션 초기화 (Node.js와 동일)
        StreamingSession session = StreamingSession.builder()
            .messageId(messageId)
            .roomId(roomId)
            .aiType(aiType)
            .content("")
            .timestamp(timestamp)
            .isStreaming(true)
            .build();
        streamingSessions.put(messageId, session);

        // AI 스트리밍 시작 알림 (Node.js 버전과 동일한 이벤트명과 구조)
        socketIOServer.getRoomOperations("room:" + roomId)
                .sendEvent("aiMessageStart", Map.of(
                    "messageId", messageId,
                    "aiType", aiType,
                    "timestamp", timestamp
                ));

        // 실제 AI 서비스 호출 (비동기)
        CompletableFuture.runAsync(() -> {
            try {
                // AI 타입에 따른 적절한 AiType enum 변환
                AiType aiTypeEnum = convertToAiType(aiType);

                if (aiTypeEnum == null) {
                    log.warn("Unknown AI type: {}", aiType);
                    streamingSessions.remove(messageId);
                    socketIOServer.getRoomOperations("room:" + roomId)
                            .sendEvent("aiMessageError", Map.of(
                                "messageId", messageId,
                                "error", "지원하지 않는 AI 타입입니다: " + aiType,
                                "aiType", aiType
                            ));
                    return;
                }

                // 스트리밍 응답 생성
                StringBuilder accumulatedContent = new StringBuilder();

                aiService.generateStreamingResponse(aiTypeEnum, query,
                    // 스트리밍 청크 콜백 - Node.js 버전과 동일한 구조
                    chunk -> {
                        accumulatedContent.append(chunk);
                        
                        // 세션 업데이트 (Node.js와 동일)
                        StreamingSession s = streamingSessions.get(messageId);
                        if (s != null) {
                            s.setContent(accumulatedContent.toString());
                        }
                        
                        // 코드 블록 감지 (Node.js와 동일)
                        boolean isCodeBlock = detectCodeBlock(chunk);
                        
                        socketIOServer.getRoomOperations("room:" + roomId)
                                .sendEvent("aiMessageChunk", Map.of(
                                    "messageId", messageId,
                                    "currentChunk", chunk,
                                    "fullContent", accumulatedContent.toString(),
                                    "isCodeBlock", isCodeBlock,
                                    "timestamp", LocalDateTime.now(),
                                    "aiType", aiType,
                                    "isComplete", false
                                ));
                    },
                    // 스트리밍 완료 콜백 - Node.js 버전과 동일한 구조
                    () -> {
                        try {
                            // 세션 정리 (Node.js와 동일)
                            streamingSessions.remove(messageId);
                            
                            // AI 메시지 저장 (Node.js 버전과 동일)
                            Message aiMessage = new Message();
                            aiMessage.setRoomId(roomId);
                            aiMessage.setContent(accumulatedContent.toString());
                            aiMessage.setType(MessageType.AI);
                            aiMessage.setAiType(convertToAiType(aiType));
                            aiMessage.setTimestamp(LocalDateTime.now());
                            aiMessage.setReactions(new java.util.HashMap<>());

                            // 메타데이터 설정
                            Message.AiMetadata metadata = Message.AiMetadata.builder()
                                .query(query)
                                .generationTime(System.currentTimeMillis() - timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                                .build();
                            aiMessage.setAiMetadata(metadata);

                            Message savedMessage = messageRepository.save(aiMessage);

                            socketIOServer.getRoomOperations("room:" + roomId)
                                    .sendEvent("aiMessageComplete", Map.of(
                                        "messageId", messageId,
                                        "_id", savedMessage.getId(),
                                        "content", accumulatedContent.toString(),
                                        "aiType", aiType,
                                        "timestamp", LocalDateTime.now(),
                                        "isComplete", true,
                                        "query", query,
                                        "reactions", new java.util.HashMap<>()
                                    ));

                            log.debug("AI streaming completed for messageId: {}", messageId);
                        } catch (Exception e) {
                            log.error("Error saving AI message for messageId: {}", messageId, e);
                            streamingSessions.remove(messageId);
                            socketIOServer.getRoomOperations("room:" + roomId)
                                    .sendEvent("aiMessageError", Map.of(
                                        "messageId", messageId,
                                        "error", "AI 메시지 저장 중 오류가 발생했습니다.",
                                        "aiType", aiType
                                    ));
                        }
                    }
                );

            } catch (Exception e) {
                log.error("AI streaming error for messageId: {}", messageId, e);
                streamingSessions.remove(messageId);
                socketIOServer.getRoomOperations("room:" + roomId)
                        .sendEvent("aiMessageError", Map.of(
                            "messageId", messageId,
                            "error", e.getMessage() != null ? e.getMessage() : "AI 응답 생성 중 오류가 발생했습니다.",
                            "aiType", aiType
                        ));
            }
        });
    }
    
    /**
     * 코드 블록 감지 로직 (Node.js와 동일)
     * Markdown 코드 블록(```)이나 들여쓰기 코드 블록(4칸 이상) 감지
     */
    private boolean detectCodeBlock(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return false;
        }
        // Markdown 코드 블록 (```)
        if (chunk.contains("```")) {
            return true;
        }
        // 들여쓰기 코드 블록 (4칸 이상으로 시작하는 라인)
        return chunk.matches("(?m)^\\s{4,}.*");
    }

    // AI 타입 문자열을 AiType enum으로 변환
    private AiType convertToAiType(String aiTypeString) {
        if (aiTypeString == null) return null;

        return switch (aiTypeString.toLowerCase()) {
            case "wayneai" -> AiType.WAYNE_AI; // wayneAI는 GPT로 매핑
            case "consultingai" -> AiType.CONSULTING_AI; // consultingAI는 Claude로 매핑
            default -> null;
        };
    }
}
