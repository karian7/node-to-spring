# Socket.IO 이벤트 핸들러 동기화 계획

**분석 완료일**: 2025-10-30  
**대상**: Node.js ↔ Java Spring Boot Socket.IO 핸들러 동기화  
**전체 호환성**: 75%

---

## 📋 작업 체크리스트

- [ ] Phase 1: Critical 보안 이슈 (4개 작업)
- [ ] Phase 2: 메모리 누수 방지 (1개 작업)
- [ ] Phase 3: 안정성 개선 (3개 작업)
- [ ] Phase 4: 성능 최적화 (3개 작업)

---

## 참고사항

userId 가져오는 부분이 리펙토링 되었습니다. (그밖의 프러퍼티도 동일)
```
// 기존코드
String userId = client.get("socket.user.id");
// 변경코드
String userId = client.get("user").getId(); // 혹은 getUserId(client) 유틸 사용
```
---

## 🔴 Phase 1: Critical 보안 이슈 (즉시 수정 필요)

### Task 1.1: FORCE_LOGIN - JWT 검증 구현
**Priority**: 🔴 Critical  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 457-488  
**심각도**: ⚠️ **보안 취약점** - 누구나 다른 사용자 세션 강제 종료 가능

**현재 문제**:
- JWT 토큰 검증 완전 누락
- 토큰의 서명, 만료, 소유자 검증 없음
- TODO 주석만 존재

**변경 내용**:
```java
// 1. JwtService 의존성 주입 추가 (클래스 레벨)
private final JwtService jwtService;

// 2. onForceLogin 메서드 내부 수정
private DataListener<Map> onForceLogin() {
    return (client, data, ackSender) -> {
        try {
            String userId = client.get("socket.user.id");
            if (userId == null) return;

            String token = (String) data.get("token");
            if (token == null) {
                client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                return;
            }

            // ✅ JWT 검증 추가
            try {
                String tokenUserId = jwtService.extractUserId(token);
                if (!userId.equals(tokenUserId)) {
                    throw new RuntimeException("Token user mismatch");
                }
            } catch (Exception e) {
                log.warn("Invalid token for force_login: {}", e.getMessage());
                client.sendEvent(ERROR, Map.of("message", "Invalid token"));
                return;
            }

            // 세션 종료 처리
            client.sendEvent(SESSION_ENDED, Map.of(
                "reason", "force_logout",
                "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
            ));

            client.disconnect();

        } catch (Exception e) {
            log.error("Force login error", e);
            client.sendEvent(ERROR, Map.of(
                "message", "세션 종료 중 오류가 발생했습니다."
            ));
        }
    };
}
```

**진행상황**: ✅ (2025-10-30) Spring SocketIOChatHandler에 JwtUtil 기반 JWT 검증 및 사용자 일치 검증 추가 완료.

**테스트 시나리오**:
1. 유효한 토큰으로 force_login → 성공
2. 다른 사용자의 토큰으로 force_login → 실패 (Invalid token)
3. 만료된 토큰으로 force_login → 실패
4. 서명이 잘못된 토큰 → 실패

---

### Task 1.2: MARK_MESSAGES_AS_READ - roomId 검증 추가
**Priority**: 🔴 Critical  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 377-415  
**심각도**: ⚠️ **보안 취약점** - 권한 없는 room의 메시지 읽음 처리 가능

**현재 문제**:
- 메시지가 실제로 해당 room에 속하는지 검증하지 않음
- Read-Modify-Write 패턴으로 성능 저하 및 Race Condition

**변경 내용**:
```java
// MessageRepository에 메서드 추가
@Query("{'_id': {$in: ?0}, 'roomId': ?1, 'readers.userId': {$ne: ?2}}")
@Update("{$push: {'readers': ?3}}")
void addReaderToMessages(List<String> messageIds, String roomId, String userId, MessageReader reader);

// onMarkMessagesAsRead 메서드 수정
private DataListener<MarkAsReadRequest> onMarkMessagesAsRead() {
    return (client, data, ackSender) -> {
        try {
            String userId = client.get("socket.user.id");
            if (userId == null) return;

            // 입력 검증 추가
            if (data.getMessageIds() == null || data.getMessageIds().isEmpty()) {
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            if (user == null) return;

            Message.MessageReader readerInfo = Message.MessageReader.builder()
                .userId(userId)
                .readAt(LocalDateTime.now())
                .build();

            // ✅ 원자적 업데이트로 변경 + roomId 검증
            messageRepository.addReaderToMessages(
                data.getMessageIds(),
                data.getRoomId(),  // roomId 검증 포함
                userId,
                readerInfo
            );

            MessagesReadResponse response = new MessagesReadResponse(
                userId,
                data.getMessageIds()
            );

            // Broadcast to room
            socketIOServer.getRoomOperations(data.getRoomId())
                .sendEvent(MESSAGES_READ, response);

        } catch (Exception e) {
            log.error("Error handling markMessagesAsRead", e);
            // ✅ 클라이언트 에러 피드백 추가
            client.sendEvent(ERROR, Map.of(
                "message", "읽음 상태 업데이트 중 오류가 발생했습니다."
            ));
        }
    };
}
```

**진행상황**: ✅ (2025-10-30) roomId 검증 및 MongoDB 원자 업데이트 적용 완료.

**테스트 시나리오**:
1. 자신이 속한 room의 메시지 → 성공
2. 속하지 않은 room의 메시지 → 업데이트 안됨 (0건)
3. 이미 읽은 메시지 → 중복 추가 안됨
4. 빈 배열 전송 → early return

---

### Task 1.3: MESSAGE_REACTION - 인증 검증 추가
**Priority**: 🔴 Critical  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 417-453  
**심각도**: ⚠️ **NPE 위험** - userId null 체크 없음

**현재 문제**:
- userId null 체크 없어 NPE 발생 가능
- 메시지 조회 실패 시 에러 피드백 없음
- 중복 리액션이어도 항상 DB 저장 (성능 이슈)

**변경 내용**:
```java
private DataListener<MessageReactionRequest> onMessageReaction() {
    return (client, data, ackSender) -> {
        try {
            String userId = client.get("socket.user.id");

            // ✅ 인증 검증 추가
            if (userId == null || userId.isEmpty()) {
                client.sendEvent(ERROR, Map.of("message", "Unauthorized"));
                return;
            }

            Message message = messageRepository.findById(data.getMessageId()).orElse(null);

            // ✅ 메시지 조회 실패 시 에러 응답
            if (message == null) {
                client.sendEvent(ERROR, Map.of("message", "메시지를 찾을 수 없습니다."));
                return;
            }

            if (message.getReactions() == null) {
                message.setReactions(new HashMap<>());
            }

            boolean changed = false;

            if ("add".equals(data.getType())) {
                Set<String> userReactions = message.getReactions()
                    .computeIfAbsent(data.getReaction(), k -> new HashSet<>());
                // ✅ 중복 체크 후 저장
                changed = userReactions.add(userId);
            } else if ("remove".equals(data.getType())) {
                changed = message.getReactions()
                    .computeIfPresent(data.getReaction(), (k, v) -> {
                        v.remove(userId);
                        return v.isEmpty() ? null : v;
                    }) != null;
            }

            // ✅ 변경사항 있을 때만 저장 및 브로드캐스트
            if (changed) {
                messageRepository.save(message);

                MessageReactionResponse response = new MessageReactionResponse(
                    message.getId(),
                    message.getReactions()
                );

                socketIOServer.getRoomOperations(message.getRoomId())
                    .sendEvent(MESSAGE_REACTION_UPDATE, response);
            }

        } catch (Exception e) {
            log.error("Error handling messageReaction", e);
            // ✅ 클라이언트 에러 피드백
            client.sendEvent(ERROR, Map.of(
                "message", "리액션 처리 중 오류가 발생했습니다."
            ));
        }
    };
}
```

**테스트 시나리오**:
1. 인증 없이 요청 → Unauthorized 에러
2. 존재하지 않는 메시지 → 에러 응답
3. 중복 리액션 추가 → DB 저장 없음
4. 리액션 추가/제거 → 1회만 브로드캐스트

---

### Task 1.4: CHAT_MESSAGE - 세션 재확인 추가
**Priority**: 🔴 Critical  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/handler/ChatMessageHandler.java`  
**라인**: 42-161  
**심각도**: ⚠️ **보안 취약점** - 세션 만료 확인 없음

**현재 문제**:
- 메시지 전송 시 세션 유효성 재확인 없음
- 세션 활동 시간 업데이트 없음

**변경 내용**:
```java
// 1. SessionService 의존성 주입 추가 (클래스 레벨)
private final SessionService sessionService;

// 2. getListener() 메서드 내부에 추가
public DataListener<Map> getListener() {
    return (client, data, ackSender) -> {
        try {
            String userId = client.get("socket.user.id");
            String sessionId = client.get("socket.user.sessionId");

            if (userId == null) {
                client.sendEvent(ERROR, Map.of(
                    "code", "UNAUTHORIZED",
                    "message", "User authentication required"
                ));
                return;
            }

            // ✅ 세션 유효성 재확인
            SessionValidationResult validation = sessionService.validateSession(
                userId, sessionId);
            if (!validation.isValid()) {
                client.sendEvent(ERROR, Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "세션이 만료되었습니다. 다시 로그인해주세요."
                ));
                return;
            }

            // ... 기존 메시지 처리 로직 ...

            // ✅ 메시지 처리 후 활동 시간 업데이트
            sessionService.updateLastActivity(userId);

            log.debug("Message processed - messageId: {}, type: {}, room: {}",
                savedMessage.getId(), savedMessage.getType(), roomId);

        } catch (Exception e) {
            // ... 에러 처리
        }
    };
}
```

**테스트 시나리오**:
1. 유효한 세션으로 메시지 전송 → 성공 + 활동 시간 업데이트
2. 만료된 세션으로 메시지 전송 → SESSION_EXPIRED 에러
3. 탈취된 세션 토큰 → 세션 검증 실패

---

## 🟠 Phase 2: 메모리 누수 방지 (긴급)

### Task 2.1: LEAVE_ROOM - 메모리 정리 로직 추가
**Priority**: 🟠 High  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 278-308  
**심각도**: ⚠️ **메모리 누수** - 장기 실행 시 메모리 누적

**현재 문제**:
- userRooms 정리 누락
- streamingSessions 정리 누락
- messageQueues, messageLoadRetries 정리 누락
- 현재 방 검증 없어 불필요한 DB 쿼리

**변경 내용**:
```java
// 1. StreamingSession에 userId 필드 추가 필요 (StreamingSession.java)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingSession {
    private String messageId;
    private String roomId;
    private String userId;  // ✅ 추가
    private String aiType;
    private String content;
    private LocalDateTime timestamp;
    private boolean isStreaming;
}

// 2. onLeaveRoom 메서드 수정
public DataListener<String> onLeaveRoom() {
    return (client, roomId, ackSender) -> {
        try {
            String userId = client.get("socket.user.id");
            String userName = client.get("socket.user.name");

            // ✅ 현재 방 확인 (메모리 체크로 불필요한 DB 쿼리 방지)
            String currentRoom = userRooms.get(userId);
            if (currentRoom == null || !currentRoom.equals(roomId)) {
                log.debug("User {} is not in room {}", userId, roomId);
                return;
            }

            User user = userRepository.findById(userId).orElse(null);
            Room room = roomRepository.findById(roomId).orElse(null);

            if (user != null && room != null) {
                room.getParticipantIds().remove(userId);
                roomRepository.save(room);

                // Leave socket room
                client.leaveRoom("room:" + roomId);

                // ✅ userRooms 정리
                userRooms.remove(userId);

                log.info("User {} left room {}", userName, room.getName());

                // ✅ 스트리밍 세션 정리
                streamingSessions.entrySet().removeIf(entry -> {
                    StreamingSession session = entry.getValue();
                    return roomId.equals(session.getRoomId()) &&
                           userId.equals(session.getUserId());
                });

                // ✅ 메시지 큐 정리
                String queueKey = roomId + ":" + userId;
                messageQueues.remove(queueKey);
                messageLoadRetries.remove(queueKey);

                // Send system message
                sendSystemMessage(roomId, userName + "님이 퇴장하였습니다.");

                // Broadcast updated participant list
                broadcastParticipantList(roomId);
            } else {
                log.warn("Room {} not found or user {} has no access", roomId, userId);
            }

        } catch (Exception e) {
            log.error("Error handling leaveRoom", e);
            client.sendEvent(ERROR, "방 퇴장 중 오류가 발생했습니다.");
        }
    };
}

// 3. ChatMessageHandler의 handleAIResponse에서 StreamingSession 생성 시 userId 추가
StreamingSession session = StreamingSession.builder()
    .messageId(messageId)
    .roomId(roomId)
    .userId(userId)  // ✅ 추가
    .aiType(aiType)
    .content("")
    .timestamp(timestamp)
    .isStreaming(true)
    .build();
```

**테스트 시나리오**:
1. 방 퇴장 → userRooms에서 제거 확인
2. AI 스트리밍 중 퇴장 → streamingSessions 정리 확인
3. 메시지 로드 중 퇴장 → messageQueues 정리 확인
4. 다른 방에서 leaveRoom 요청 → early return (DB 쿼리 없음)

---

## 🟡 Phase 3: 안정성 개선 (중요)

### Task 3.1: JOIN_ROOM - 읽음 상태 자동 업데이트
**Priority**: 🟡 Medium  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 676-727 (loadInitialMessages 메서드)

**현재 문제**:
- 메시지 로드 시 읽음 상태 자동 업데이트 누락

**변경 내용**:
```java
private FetchMessagesResponse loadInitialMessages(String roomId) {
    try {
        // ... 기존 메시지 로드 로직 ...

        // ✅ 읽음 상태 업데이트 추가
        if (!sortedMessages.isEmpty()) {
            String userId = getCurrentUserId(); // Context에서 가져오기
            if (userId != null) {
                CompletableFuture.runAsync(() -> {
                    updateReadStatus(sortedMessages, userId);
                });
            }
        }

        return FetchMessagesResponse.builder()
            .messages(messageResponses)
            .hasMore(hasMore)
            .oldestTimestamp(oldestTimestamp)
            .build();

    } catch (Exception e) {
        log.error("Error loading initial messages for room {}", roomId, e);
        return FetchMessagesResponse.builder()
            .messages(new ArrayList<>())
            .hasMore(false)
            .oldestTimestamp(null)
            .build();
    }
}
```

---

### Task 3.2: JOIN_ROOM & FETCH_PREVIOUS_MESSAGES - 타임아웃 추가
**Priority**: 🟡 Medium  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 491-562, 676-727

**현재 문제**:
- 메시지 로드 시 타임아웃 처리 없음
- 매우 느린 쿼리 시 클라이언트 무한 대기

**변경 내용**:
```java
// 상수 추가
private static final int MESSAGE_LOAD_TIMEOUT = 10000; // 10초

// loadMessagesWithRetry 메서드 수정
private CompletableFuture<FetchMessagesResponse> loadMessagesWithRetry(
        SocketIOClient client, String roomId, LocalDateTime before, String retryKey) {

    return CompletableFuture.supplyAsync(() -> {
        // 기존 로직...
    })
    .orTimeout(MESSAGE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS)
    .exceptionally(throwable -> {
        if (throwable instanceof TimeoutException) {
            log.debug("Message load timeout - roomId: {}, before: {}", roomId, before);
            throw new RuntimeException("Message loading timed out");
        }
        throw new RuntimeException(throwable);
    });
}
```

---

### Task 3.3: 모든 이벤트 - 클라이언트 에러 피드백 표준화
**Priority**: 🟡 Medium  
**파일**: 모든 핸들러 메서드

**현재 문제**:
- 일부 핸들러에서 에러 발생 시 로그만 기록
- 클라이언트가 실패 원인을 알 수 없음

**변경 내용**:
- 모든 catch 블록에 client.sendEvent(ERROR, ...) 추가
- 에러 응답 구조 통일: `{ "message": "..." }` 또는 `{ "code": "...", "message": "..." }`

---

## 🟢 Phase 4: 성능 최적화 (권장)

### Task 4.1: JOIN_ROOM - Room 업데이트 Atomic 연산
**Priority**: 🟢 Low  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 207-208

**변경 내용**:
```java
// RoomRepository에 메서드 추가
@Query("{'_id': ?0}")
@Update("{'$addToSet': {'participantIds': ?1}}")
Room addParticipant(String roomId, String userId);
```

---

### Task 4.2: 입장 메시지 표현 통일
**Priority**: 🟢 Low  
**파일**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`  
**라인**: 217

**변경 내용**:
```java
.content(userName + "님이 입장하였습니다.")  // "이" → "님이"
```

---

### Task 4.3: 상세 디버그 로깅 추가
**Priority**: 🟢 Low  
**파일**: 모든 핸들러

**변경 내용**:
- Node.js의 logDebug 유틸리티와 유사한 구조화된 로깅
- messageId, userId, roomId 등 컨텍스트 정보 포함

---

## 📝 작업 순서

1. **Phase 1 완료** (보안) → 배포 **필수**
2. **Phase 2 완료** (메모리) → 배포 **필수**
3. **Phase 3** → 점진적 배포
4. **Phase 4** → 선택적 배포

---

## ✅ 검증 체크리스트

각 작업 완료 후:
- [ ] 단위 테스트 작성 및 통과
- [ ] Node.js 구현과 동작 일치 확인
- [ ] 에러 시나리오 테스트
- [ ] 메모리 프로파일링 (Phase 2)
- [ ] 성능 벤치마크 (Phase 4)

---

## 📚 참고 파일

- **Node.js 구현**: `backend/sockets/chat.js`
- **Java 구현**: `src/main/java/com/example/chatapp/websocket/socketio/SocketIOChatHandler.java`
- **분석 문서**: 본 파일

---

**작성자**: Claude Code  
**최종 업데이트**: 2025-10-30
