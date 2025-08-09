package com.example.chatapp.websocket;

import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.SendMessageRequest;
import com.example.chatapp.dto.UserSummaryResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

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

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest chatMessage, Principal principal) {
        User sender = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        roomRepository.findById(chatMessage.getRoomId())
                .orElseThrow(() -> new RuntimeException("Room not found"));

        Message message = new Message();
        message.setSenderId(sender.getId());
        message.setRoomId(chatMessage.getRoomId());
        message.setContent(chatMessage.getContent());
        message.setType(chatMessage.getType());

        Message savedMessage = messageRepository.save(message);

        MessageResponse messageResponse = mapToMessageResponse(savedMessage, sender);

        messagingTemplate.convertAndSend("/topic/rooms/" + chatMessage.getRoomId(), messageResponse);
    }

    private MessageResponse mapToMessageResponse(Message message, User sender) {
        UserSummaryResponse senderSummary = null;
        if (sender != null) {
            senderSummary = new UserSummaryResponse(
                    sender.getId(),
                    sender.getName(),
                    sender.getEmail()
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
}
