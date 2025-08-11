package com.example.chatapp.websocket;

import com.example.chatapp.dto.*;
import com.example.chatapp.dto.ai.AiMessageChunk;
import com.example.chatapp.dto.ai.AiMessageComplete;
import com.example.chatapp.dto.ai.AiMessageStart;
import com.example.chatapp.model.*;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Controller
public class ChatController {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private AiService aiService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest chatMessage, Principal principal) {
        User sender = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

        roomRepository.findById(chatMessage.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setRoomId(chatMessage.getRoomId());
        message.setContent(chatMessage.getContent());
        message.setType(chatMessage.getType());
        message.setTimestamp(LocalDateTime.now());

        if (chatMessage.getType() == MessageType.FILE) {
            if (chatMessage.getFileId() == null) {
                // Handle error: file ID is required for file messages
                return;
            }
            com.example.chatapp.model.File file = fileRepository.findById(chatMessage.getFileId())
                    .orElseThrow(() -> new RuntimeException("File not found"));

            // Optional: Check if the sender owns the file
            if (!file.getUploadedBy().equals(sender.getId())) {
                // Handle error: user does not have permission for this file
                return;
            }

            message.setFileId(file.getId());
            message.setMetadata(Message.FileMetadata.builder()
                    .fileType(file.getMimetype())
                    .fileSize(file.getSize())
                    .originalName(file.getOriginalname())
                    .build());
        }

        Message savedMessage = messageRepository.save(message);

        MessageResponse messageResponse = mapToMessageResponse(savedMessage, sender);

        messagingTemplate.convertAndSend("/topic/rooms/" + chatMessage.getRoomId(), messageResponse);

        handleAiMentions(savedMessage);
    }

    private void handleAiMentions(Message originalMessage) {
        String content = originalMessage.getContent();
        List<String> mentions = extractAIMentions(content);

        for (String mention : mentions) {
            String query = content.replaceAll("(?i)@(wayneAI|consultingAI)", "").trim();
            String tempId = mention + "-" + System.currentTimeMillis();
            StringBuilder accumulatedContent = new StringBuilder();

            // Convert mention to enum name format (e.g., "wayneAI" -> "WAYNE_AI")
            String enumMention = mention.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
            AiType aiType = AiType.valueOf(enumMention);

            aiService.generateResponse(query, mention)
                .doOnSubscribe(subscription -> {
                    // Send AI_MESSAGE_START
                    AiMessageStart startEvent = new AiMessageStart(tempId, aiType, LocalDateTime.now());
                    messagingTemplate.convertAndSend("/topic/rooms/" + originalMessage.getRoomId() + "/ai.stream.start", startEvent);
                })
                .doOnError(error -> {
                    // Send AI_MESSAGE_ERROR
                    log.error("Error generating AI response", error);
                    // You would create a proper error DTO here
                    messagingTemplate.convertAndSend("/topic/rooms/" + originalMessage.getRoomId() + "/ai.stream.error", "Error generating response");
                })
                .doOnComplete(() -> {
                    // Save the final message and send AI_MESSAGE_COMPLETE
                    Message aiMessage = new Message();
                    aiMessage.setRoomId(originalMessage.getRoomId());
                    aiMessage.setContent(accumulatedContent.toString());
                    aiMessage.setType(MessageType.AI);
                    aiMessage.setAiType(aiType);
                    aiMessage.setTimestamp(LocalDateTime.now());
                    Message savedAiMessage = messageRepository.save(aiMessage);

                    AiMessageComplete completeEvent = new AiMessageComplete(
                            tempId,
                            savedAiMessage.getId(),
                            savedAiMessage.getContent(),
                            savedAiMessage.getAiType(),
                            savedAiMessage.getTimestamp(),
                            true,
                            query,
                            savedAiMessage.getReactions()
                    );
                    messagingTemplate.convertAndSend("/topic/rooms/" + originalMessage.getRoomId() + "/ai.stream.complete", completeEvent);
                })
                .subscribe(chunk -> {
                    // Send AI_MESSAGE_CHUNK
                    accumulatedContent.append(chunk);
                    AiMessageChunk chunkEvent = new AiMessageChunk(
                            tempId,
                            chunk,
                            accumulatedContent.toString(),
                            false, // isCodeBlock detection would be more complex
                            LocalDateTime.now(),
                            aiType,
                            false
                    );
                    messagingTemplate.convertAndSend("/topic/rooms/" + originalMessage.getRoomId() + "/ai.stream.chunk", chunkEvent);
                });
        }
    }

    private List<String> extractAIMentions(String content) {
        if (content == null) return java.util.Collections.emptyList();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)@(wayneAI|consultingAI)");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        List<String> mentions = new java.util.ArrayList<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions.stream().distinct().collect(Collectors.toList());
    }

    @Transactional
    @MessageMapping("/chat.joinRoom")
    public void joinRoom(@Payload RoomIdRequest request, Principal principal) {
        String roomId = request.getRoomId();
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        room.getParticipantIds().add(user.getId());
        roomRepository.save(room);

        log.info("User {} joined room {}", user.getName(), room.getName());

        // Send system message
        sendSystemMessage(roomId, user.getName() + "님이 입장하였습니다.");

        // Broadcast updated participant list
        broadcastParticipantList(roomId);
    }

    @Transactional
    @MessageMapping("/chat.leaveRoom")
    public void leaveRoom(@Payload RoomIdRequest request, Principal principal) {
        String roomId = request.getRoomId();
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        room.getParticipantIds().remove(user.getId());
        roomRepository.save(room);

        log.info("User {} left room {}", user.getName(), room.getName());

        // Send system message
        sendSystemMessage(roomId, user.getName() + "님이 퇴장하였습니다.");

        // Broadcast updated participant list
        broadcastParticipantList(roomId);
    }

    private void sendSystemMessage(String roomId, String content) {
        Message message = new Message();
        message.setRoomId(roomId);
        message.setContent(content);
        message.setType(MessageType.SYSTEM);
        message.setTimestamp(LocalDateTime.now());
        Message savedMessage = messageRepository.save(message);

        MessageResponse messageResponse = mapToMessageResponse(savedMessage, null); // No sender for system messages
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, messageResponse);
    }

    private void broadcastParticipantList(String roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        List<UserSummaryResponse> participants = userRepository.findAllById(room.getParticipantIds()).stream()
                .map(p -> new UserSummaryResponse(p.getId(), p.getName(), p.getEmail(), p.getProfileImage()))
                .collect(Collectors.toList());

        ParticipantListResponse response = new ParticipantListResponse(roomId, participants);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/participants", response);
    }

    private MessageResponse mapToMessageResponse(Message message, User sender) {
        UserSummaryResponse senderSummary = null;
        if (sender != null) {
            senderSummary = new UserSummaryResponse(
                    sender.getId(),
                    sender.getName(),
                    sender.getEmail(),
                    sender.getProfileImage()
            );
        }

        return new MessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getContent(),
                senderSummary,
                message.getType(),
                null, // File response can be added later
                message.getAiType(),
                message.getMentions(),
                message.getTimestamp()
        );
    }

    @MessageMapping("/chat.fetchPreviousMessages")
    public void fetchPreviousMessages(@Payload FetchMessagesRequest request, Principal principal) {
        final int BATCH_SIZE = 30;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(0, BATCH_SIZE, org.springframework.data.domain.Sort.by("timestamp").descending());

        LocalDateTime before = request.getBefore() == null ? LocalDateTime.now() : request.getBefore();

        Page<Message> messagePage = messageRepository.findByRoomIdAndTimestampBefore(request.getRoomId(), before, pageable);

        List<Message> messages = messagePage.getContent();

        // Fetch users for the messages
        Set<String> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> userMap = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<MessageResponse> messageResponses = messages.stream()
                .map(message -> mapToMessageResponse(message, userMap.get(message.getSenderId())))
                .collect(Collectors.toList());

        FetchMessagesResponse response = new FetchMessagesResponse(messageResponses, messagePage.hasNext());

        messagingTemplate.convertAndSendToUser(
                principal.getName(),
                "/topic/messages.history",
                response
        );
    }

    @MessageMapping("/chat.markMessagesAsRead")
    public void markMessagesAsRead(@Payload MarkAsReadRequest request, Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

        List<Message> messages = messageRepository.findAllById(request.getMessageIds());

        Message.ReaderInfo readerInfo = Message.ReaderInfo.builder()
                .userId(user.getId())
                .readAt(LocalDateTime.now())
                .build();

        messages.forEach(message -> {
            if (message.getReaders() == null) {
                message.setReaders(new java.util.ArrayList<>());
            }
            // Add reader only if they haven't read it before
            boolean alreadyRead = message.getReaders().stream().anyMatch(r -> r.getUserId().equals(user.getId()));
            if (!alreadyRead) {
                message.getReaders().add(readerInfo);
            }
        });

        messageRepository.saveAll(messages);

        MessagesReadResponse response = new MessagesReadResponse(user.getId(), request.getMessageIds());
        messagingTemplate.convertAndSend("/topic/rooms/" + request.getRoomId() + "/messages.read", response);
    }

    @MessageMapping("/chat.messageReaction")
    public void messageReaction(@Payload MessageReactionRequest request, Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (message.getReactions() == null) {
            message.setReactions(new java.util.HashMap<>());
        }

        if ("add".equals(request.getType())) {
            message.getReactions()
                .computeIfAbsent(request.getReaction(), k -> new java.util.HashSet<>())
                .add(user.getId());
        } else if ("remove".equals(request.getType())) {
            message.getReactions()
                .computeIfPresent(request.getReaction(), (k, v) -> {
                    v.remove(user.getId());
                    return v.isEmpty() ? null : v;
                });
        }

        messageRepository.save(message);

        MessageReactionResponse response = new MessageReactionResponse(message.getId(), message.getReactions());
        messagingTemplate.convertAndSend("/topic/rooms/" + message.getRoomId() + "/messages.reaction", response);
    }
}
