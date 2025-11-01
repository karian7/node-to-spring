package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * 메시지 조회 처리 핸들러
 * 이전 메시지 조회, 재시도 로직, 읽음 상태 업데이트 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageFetchHandler {

    private final RoomRepository roomRepository;
    private final MessageLoader messageLoader;
    private final RetryTemplate retryTemplate;

    private final Map<String, Boolean> messageQueues = new ConcurrentHashMap<>();
    
    @OnEvent(FETCH_PREVIOUS_MESSAGES)
    public void handleFetchMessages(SocketIOClient client, FetchMessagesRequest data) {
        String userId = getUserId(client);
        String queueKey = data.roomId() + ":" + userId;
        if (userId == null) {
            client.sendEvent(ERROR, Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "인증이 필요합니다."
            ));
            return;
        }
        
        try {
            // 이미 로드 중인지 확인
            if (Boolean.TRUE.equals(messageQueues.get(queueKey))) {
                log.debug("Message load skipped - already loading for user {} in room {}",
                        userId, data.roomId());
                return;
            }

            // 권한 체크
            Room room = roomRepository.findById(data.roomId()).orElse(null);
            if (room == null || !room.getParticipantIds().contains(userId)) {
                client.sendEvent(ERROR, Map.of(
                        "code", "LOAD_ERROR",
                        "message", "채팅방 접근 권한이 없습니다."
                ));
                return;
            }

            messageQueues.put(queueKey, true);
            client.sendEvent(MESSAGE_LOAD_START);

            log.debug("Starting message load for user {} in room {}, limit: {}",
                    userId, data.roomId(), data.limit());

            // RetryTemplate을 사용한 동기 방식 메시지 로드
            FetchMessagesResponse result = retryTemplate.execute(context -> {
                log.debug("Loading messages for room {} (attempt {})",
                        data.roomId(), context.getRetryCount() + 1);
                return messageLoader.loadMessages(data.roomId(), data.limit(), userId);
            });
            
            log.debug("Previous messages loaded - room: {}, count: {}, hasMore: {}, oldestTimestamp: {}",
                    data.roomId(), result.getMessages().size(),
                    result.isHasMore(), result.getOldestTimestamp());
            
            client.sendEvent(PREVIOUS_MESSAGES_LOADED, result);

        } catch (Exception e) {
            log.error("Error handling fetchPreviousMessages", e);
            client.sendEvent(ERROR, Map.of(
                    "code", "LOAD_ERROR",
                    "message", e.getMessage() != null ?
                            e.getMessage() : "이전 메시지를 불러오는 중 오류가 발생했습니다."
            ));
        } finally {
            messageQueues.remove(queueKey);
        }
    }

    private String getUserId(SocketIOClient client) {
        var user = (SocketUser) client.get("user");
        return user.id();
    }
}
