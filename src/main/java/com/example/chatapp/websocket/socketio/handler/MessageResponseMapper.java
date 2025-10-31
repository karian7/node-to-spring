package com.example.chatapp.websocket.socketio.handler;

import com.example.chatapp.dto.FileResponse;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.Message;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * 메시지를 응답 DTO로 변환하는 매퍼
 * 파일 정보, 사용자 정보 등을 포함한 MessageResponse 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageResponseMapper {

    private final FileRepository fileRepository;

    /**
     * Message 엔티티를 MessageResponse DTO로 변환
     * 
     * @param message 변환할 메시지 엔티티
     * @param sender 메시지 발신자 정보 (null 가능)
     * @return MessageResponse DTO
     */
    public MessageResponse mapToMessageResponse(Message message, User sender) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.toTimestampMillis())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ? 
                        message.getReactions() : new HashMap<>())
                .readers(message.getReaders() != null ? 
                        message.getReaders() : new ArrayList<>());

        // 발신자 정보 설정
        if (sender != null) {
            builder.sender(UserResponse.builder()
                    .id(sender.getId())
                    .name(sender.getName())
                    .email(sender.getEmail())
                    .profileImage(sender.getProfileImage())
                    .build());
        }

        // 파일 정보 설정
        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId()).ifPresent(file -> 
                    builder.file(FileResponse.builder()
                            .id(file.getId())
                            .filename(file.getFilename())
                            .originalname(file.getOriginalname())
                            .mimetype(file.getMimetype())
                            .size(file.getSize())
                            .build()));
        }

        // 메타데이터 설정
        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }
}
