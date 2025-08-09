package com.example.chatapp.controller;

import com.example.chatapp.dto.FileUploadResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserSummaryResponse;
import com.example.chatapp.model.File;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/rooms/{roomId}/messages")
public class MessageController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessagesForRoom(@PathVariable String roomId, Pageable pageable) {
        roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

        Page<Message> messages = messageRepository.findByRoomId(roomId, pageable);
        Page<MessageResponse> messageResponses = messages.map(this::mapToMessageResponse);

        return ResponseEntity.ok(messageResponses);
    }

    private MessageResponse mapToMessageResponse(Message message) {
        UserSummaryResponse senderSummary = null;
        if (message.getSenderId() != null) {
            User sender = userRepository.findById(message.getSenderId()).orElse(null);
            if (sender != null) {
                senderSummary = new UserSummaryResponse(
                        sender.getId(),
                        sender.getName(),
                        sender.getEmail()
                );
            }
        }

        FileUploadResponse fileResponse = null;
        if (message.getFileId() != null) {
            File file = fileRepository.findById(message.getFileId()).orElse(null);
            if (file != null) {
                String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/api/files/download/")
                    .path(file.getFilename())
                    .toUriString();
                fileResponse = new FileUploadResponse(
                        file.getFilename(),
                        file.getOriginalname(),
                        file.getMimetype(),
                        file.getSize(),
                        fileDownloadUri
                );
            }
        }

        return new MessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getContent(),
                senderSummary,
                message.getType(),
                fileResponse,
                message.getAiType(),
                message.getMentions(),
                message.getTimestamp()
        );
    }
}
