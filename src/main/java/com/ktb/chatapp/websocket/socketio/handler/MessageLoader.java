package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageReadStatusService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageLoader {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;

    private static final int BATCH_SIZE = 30;

    /**
     * 메시지 로드 (RetryTemplate에서 호출)
     */
    public FetchMessagesResponse loadMessages(String roomId, Integer limit, String userId) {
        int batchSize = limit != null && limit > 0 ? limit : BATCH_SIZE;
        return loadMessagesInternal(roomId, batchSize, userId);
    }

    /**
     * 초기 메시지 로드 (방 입장 시) - 동기
     */
    public FetchMessagesResponse loadInitialMessages(String roomId, String userId) {
        try {
            return loadMessagesInternal(roomId, BATCH_SIZE, userId);
        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", roomId, e);
            return FetchMessagesResponse.builder()
                    .messages(new ArrayList<>())
                    .hasMore(false)
                    .oldestTimestamp(null)
                    .build();
        }
    }

    private FetchMessagesResponse loadMessagesInternal(
            String roomId,
            int batchSize,
            String userId) {
        
        Pageable pageable = PageRequest.of(0, batchSize, Sort.by("timestamp").descending());
        LocalDateTime beforeTime = LocalDateTime.now();

        Page<Message> messagePage = messageRepository
                .findByRoomIdAndIsDeletedAndTimestampBefore(roomId, false, beforeTime, pageable);

        List<Message> messages = messagePage.getContent();

        // DESC로 조회했으므로 ASC로 재정렬 (채팅 UI 표시 순서)
        List<Message> sortedMessages = messages.reversed();
        
        messageReadStatusService.updateReadStatusAsync(sortedMessages, userId);
        
        Function<String, User> userFunction = id -> userRepository.findById(id)
                .orElse(null);
        
        // 메시지 응답 생성
        List<MessageResponse> messageResponses = sortedMessages.stream()
                .map(message -> {
                    var user = userFunction.apply(message.getSenderId());
                    return messageResponseMapper.mapToMessageResponse(message, user);
                })
                .collect(Collectors.toList());

        boolean hasMore = messagePage.hasNext();

        log.debug("Messages loaded - roomId: {}, limit: {}, count: {}, hasMore: {}",
                roomId, batchSize, messageResponses.size(), hasMore);

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(hasMore)
                .oldestTimestamp(convertToIsoTimestamp(sortedMessages))
                .build();
    }

    /**
     * LocalDateTime을 ISO_INSTANT 형식으로 변환
     */
    private String convertToIsoTimestamp(List<Message> messages) {
        if (messages.isEmpty()) {
            return null;
        }

        Message firstMessage = messages.getFirst();
        if (firstMessage.getTimestamp() == null) {
            return null;
        }
        
        return firstMessage.getTimestamp()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toString();
    }
}
