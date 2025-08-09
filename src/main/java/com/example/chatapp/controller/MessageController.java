package com.example.chatapp.controller;

import com.example.chatapp.dto.FileUploadResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserSummaryResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.Room;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
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

    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessagesForRoom(@PathVariable Long roomId, Pageable pageable) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

        Page<Message> messages = messageRepository.findByRoom(room, pageable);
        Page<MessageResponse> messageResponses = messages.map(this::mapToMessageResponse);

        return ResponseEntity.ok(messageResponses);
    }

    private MessageResponse mapToMessageResponse(Message message) {
        UserSummaryResponse senderSummary = null;
        if (message.getSender() != null) {
            senderSummary = new UserSummaryResponse(
                    message.getSender().getId(),
                    message.getSender().getName(),
                    message.getSender().getEmail()
            );
        }

        FileUploadResponse fileResponse = null;
        if (message.getFile() != null) {
            String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/download/")
                .path(message.getFile().getFilename())
                .toUriString();
            fileResponse = new FileUploadResponse(
                    message.getFile().getFilename(),
                    message.getFile().getOriginalname(),
                    message.getFile().getMimetype(),
                    message.getFile().getSize(),
                    fileDownloadUri
            );
        }

        return new MessageResponse(
                message.getId(),
                message.getRoom().getId(),
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
