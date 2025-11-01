package com.ktb.chatapp.service;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 메시지 읽음 상태 관리 서비스
 * 비동기로 메시지 읽음 상태를 업데이트
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageReadStatusService {

    private final MessageRepository messageRepository;

    /**
     * 메시지 읽음 상태 비동기 업데이트
     * MongoDB의 saveAll()은 각 도큐먼트를 개별적으로 업데이트하며,
     * 단일 도큐먼트 작업은 원자적으로 수행됨
     *
     * @param messages 읽음 상태를 업데이트할 메시지 리스트
     * @param userId 읽은 사용자 ID
     */
    @Async
    public void updateReadStatusAsync(List<Message> messages, String userId) {
        if (messages.isEmpty()) {
            return;
        }
        
        try {
            List<String> messageIds = messages.stream()
                    .map(Message::getId)
                    .collect(Collectors.toList());

            List<Message> messagesToUpdate = messageRepository.findAllById(messageIds);

            Message.MessageReader readerInfo = Message.MessageReader.builder()
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .build();

            messagesToUpdate.forEach(message -> {
                if (message.getReaders() == null) {
                    message.setReaders(new ArrayList<>());
                }
                boolean alreadyRead = message.getReaders().stream()
                        .anyMatch(r -> r.getUserId().equals(userId));
                if (!alreadyRead) {
                    message.getReaders().add(readerInfo);
                }
            });

            messageRepository.saveAll(messagesToUpdate);
            
            log.debug("Read status updated for {} messages by user {}",
                    messagesToUpdate.size(), userId);

        } catch (Exception e) {
            log.error("Read status update error for user {}", userId, e);
        }
    }
}
