---
title: 메시지 기록 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/routes/api/message.js
  - backend/controllers/messageController.js
  - backend/models/Message.js
  - backend/sockets/chat.js
  - backend/models/File.js
  - docs/socketio-handler-sync-plan.md
---

## 기능 개요
- 채팅 메시지 저장/조회/읽음/리액션 처리를 담당한다.
- REST `GET /api/message/rooms/:roomId/messages`는 미구현 상태로 500을 반환하며, 현재 조회는 Socket.IO 이벤트(`fetchPreviousMessages`, `joinRoom`)로만 동작한다.
- 메시지는 MongoDB `Message` 컬렉션에 저장되고, 파일 첨부(`File` 문서 참조)와 AI/시스템 메시지를 포함한다.
- 소켓 계층은 메시지를 배치로 로드하고, 읽음 상태와 리액션을 브로드캐스트하며, AI 호출 시 스트리밍 응답을 처리한다.

## 사용자 시나리오
- **메시지 초기 로드**: 클라이언트가 방에 입장(`joinRoom`)하면 최근 메시지 배치를 받아 `joinRoomSuccess` 이벤트로 수신한다.
- **이전 메시지 로드**: `fetchPreviousMessages` 이벤트로 페이징하며, 서버는 재시도/타임아웃 로직을 갖고 있다.
- **메시지 전송**: `chatMessage` 이벤트로 텍스트/파일 메시지를 전송하고, 필요 시 AI 멘션을 트리거한다.
- **AI 스트리밍**: `@wayneAI`, `@consultingAI` 멘션을 포함하면 `aiMessageStart` → `aiMessageChunk` → `aiMessageComplete` 순으로 브로드캐스트된다. (페르소나/스트림 세부 사양은 [`ai-integration.md`](ai-integration.md) 참조)
- **읽음 처리**: `markMessagesAsRead` 이벤트로 읽음 목록을 업데이트하고 `messagesRead` 이벤트로 전달한다.
- **리액션**: `messageReaction` 이벤트로 이모지 리액션 추가/제거, `messageReactionUpdate` 전파.
- **REST 개발 TODO**: Spring 마이그레이션 시 REST 엔드포인트를 완성해 Socket 전용 흐름과 동일한 응답 스키마를 제공해야 한다.

## HTTP 인터페이스 (현재 Node)
| 타입 | 메서드 | 경로 | 인증 | 상태 |
| --- | --- | --- | --- | --- |
| HTTP | GET | `/api/message/rooms/:roomId/messages` | `auth` | 미구현 (500 반환) |

### 예상 REST 응답 스키마 (마이그레이션 타깃)
- **Response 200 (예상)**
```json
{
  "success": true,
  "data": [
    {
      "_id": "6561...",
      "room": "6560...",
      "type": "text",
      "content": "안녕하세요",
      "sender": {
        "_id": "655f...",
        "name": "홍길동",
        "email": "user@example.com",
        "profileImage": ""
      },
      "file": null,
      "timestamp": "2025-10-30T06:30:00.000Z",
      "readers": [
        { "userId": "655f...", "readAt": "2025-10-30T06:31:00.000Z" }
      ],
      "reactions": {},
      "metadata": {}
    }
  ],
  "pagination": {
    "hasMore": true,
    "oldestTimestamp": "2025-10-29T23:59:59.000Z"
  }
}
```
- **Response 500 (현행)**
```json
{
  "success": false,
  "message": "미구현."
}
```

## Socket 인터페이스
| 이벤트 | 방향 | 페이로드 | 설명 |
| --- | --- | --- | --- |
| `fetchPreviousMessages` | client→server | `{ roomId, before? }` | 배치 메시지 로드 요청. 서버는 큐/재시도로 중복 요청 방지 |
| `messageLoadStart` | server→client | none | 메시지 로드 시작 알림 |
| `previousMessagesLoaded` | server→client | `{ messages, hasMore, oldestTimestamp }` | 이전 메시지 배치 반환 |
| `joinRoom` | client→server | `roomId` | 방 참가와 동시에 메시지 초기화 |
| `joinRoomSuccess` | server→client | `{ roomId, participants, messages, hasMore, oldestTimestamp, activeStreams }` | 입장 성공 + 메시지 데이터 |
| `joinRoomError` | server→client | `{ message }` | 입장 실패 |
| `message` | server→client | `Message` 객체 | 새 메시지/시스템 메시지 브로드캐스트 |
| `chatMessage` | client→server | `{ room, type, content?, fileData? }` | 텍스트/파일 메시지 전송 |
| `aiMessageStart` | server→client | `{ messageId, aiType, timestamp }` | AI 스트리밍 시작 |
| `aiMessageChunk` | server→client | `{ messageId, currentChunk, fullContent, isCodeBlock, aiType, timestamp, isComplete:false }` |
| `aiMessageComplete` | server→client | `{ messageId, _id, content, aiType, timestamp, reactions:{} }` | AI 메시지 확정 저장 |
| `aiMessageError` | server→client | `{ messageId, error, aiType }` | AI 생성 실패 |
| `markMessagesAsRead` | client→server | `{ roomId, messageIds }` | 읽음 처리 요청 |
| `messagesRead` | server→room | `{ userId, messageIds }` | 다른 참가자에게 읽음 상태 통지 |
| `messageReaction` | client→server | `{ messageId, reaction, type }` | 리액션 추가/제거 |
| `messageReactionUpdate` | server→room | `{ messageId, reactions }` | 리액션 변경 브로드캐스트 |

## 메시지/이벤트 페이로드 스키마
- **Message (Socket 브로드캐스트)**
```json
{
  "_id": "6561...",
  "room": "6560...",
  "type": "text" | "file" | "system" | "ai",
  "content": "문자열 또는 ''",
  "sender": {
    "_id": "655f...",
    "name": "홍길동",
    "email": "user@example.com",
    "profileImage": ""
  },
  "file": {
    "_id": "FileId",
    "filename": "1700000000000_abcd1234.pdf",
    "originalname": "보고서.pdf",
    "mimetype": "application/pdf",
    "size": 123456
  },
  "timestamp": "2025-10-30T06:30:00.000Z",
  "readers": [ { "userId": "655f...", "readAt": "2025-10-30T06:31:00.000Z" } ],
  "reactions": { "👍": ["655f..."] },
  "metadata": { "fileType": "application/pdf" }
}
```
- **previousMessagesLoaded**
```json
{
  "messages": [ /* Message[] 오름차순 */ ],
  "hasMore": true,
  "oldestTimestamp": "2025-10-29T23:59:59.000Z"
}
```
- **messagesRead**
```json
{
  "userId": "655f...",
  "messageIds": ["6561...","6562..."]
}
```
- **messageReactionUpdate**
```json
{
  "messageId": "6561...",
  "reactions": { "🔥": ["655f...","655e..."] }
}
```

## 예외/오류 스키마
- **fetchPreviousMessages 중복 요청**: 서버 로그로만 기록하고 추가 응답 없음 (`message load skipped` 로그).
- **loadMessages 타임아웃**: 재시도 후 실패 시 `error` 이벤트 `{ type: 'LOAD_ERROR', message: '이전 메시지를 불러오는 중 오류가 발생했습니다.' }`.
- **chatMessage 유효성 실패**: `socket.emit('error', { message: '메시지 데이터가 없습니다.' })` 등 메시지 문자열.
- **파일 메시지 권한 없음**: 오류 메시지 `파일을 찾을 수 없거나 접근 권한이 없습니다.`.
- **AI 응답 오류**: `aiMessageError` `{ messageId, error: 'AI 응답 생성 중 오류가 발생했습니다.', aiType }`.
- **markMessagesAsRead 오류**: `socket.emit('error', { message: '읽음 상태 업데이트 중 오류가 발생했습니다.' })`.
- **messageReaction 오류**: `socket.emit('error', { message: '리액션 처리 중 오류가 발생했습니다.' })`.
- **REST loadMessages**: 항상 500 `{ success:false, message:'미구현.' }` (현행).

## 데이터 모델
- **Message (MongoDB)**
  - 필수 필드: `room`(String), `content`(type≠file), `sender`(User), `type`(`text|system|ai|file`), `timestamp`, `readers`, `reactions`, `metadata`.
  - 인덱스: room+timestamp, room+isDeleted, readers.userId, sender, type, timestamp.
  - 메서드: `addReaction`, `removeReaction`, `softDelete`; static `markAsRead`.
  - Hooks: pre-save (trim content, dedupe mentions), pre-remove(파일 삭제), pre-save 비밀번호? (없음).
  - 파일 메시지의 업로드/권한 플로우는 [`file-handling.md`](file-handling.md)와 연동된다.
- **File (MongoDB)**
  - 메시지 첨부 시 `fileData._id`로 참조, `metadata`에 파일 타입/크기 저장.

## 비즈니스 규칙
- 메시지 길이 최대 10,000자, 공백만 있는 메시지는 전송하지 않음.
- 파일 메시지는 업로드한 사용자만 전송 가능하며, metadata에 파일 세부 정보를 저장.
- 시스템 메시지는 입/퇴장 등 이벤트 발생 시 생성.
- AI 멘션(@wayneAI/@consultingAI)은 중복 없이 추출되며, AI 응답은 메시지로 영구 저장된다.
- 읽음 처리: 동일 메시지에 동일 사용자 중복 삽입 방지 (`$push` 조건 및 소켓 후속 저장).
- 메시지 로드는 BATCH_SIZE=30, 재시도 3회, 지수 백오프(최대 10초).
- 타임아웃 10초 초과 시 오류 반환.

## 에러/예외 요약
| 경로/이벤트 | 조건 | 응답/이벤트 |
| --- | --- | --- |
| GET `/message/rooms/:roomId/messages` | 모든 요청 | 500 `{ success:false, message:'미구현.' }` |
| Socket `fetchPreviousMessages` | 재시도 한계 초과 | `error` 이벤트 (LOAD_ERROR) |
| Socket `chatMessage` | 권한/데이터 오류 | `error` `{ message: ... }` |
| Socket `messageReaction` | 메시지 미존재 | `error` `{ message:'메시지를 찾을 수 없습니다.' }` |
| Socket `aiMessage` | OpenAI 오류 | `aiMessageError` + 로그 |

## 외부 의존성
- **MongoDB/Mongoose**: 메시지, 파일, 읽음 상태 관리.
- **Socket.IO**: 실시간 메시지/읽음/리액션/AI 스트림 전송.
- **OpenAI API** (`aiService`): AI 응답 스트리밍.
- **Redis SessionService**: 메시지 전송 전 세션 유효성 검사.

## 마이그레이션 메모
- REST 엔드포인트를 구현해 Socket과 동일한 스키마를 제공해야 하며, 페이지네이션 및 정렬 옵션을 정의해야 한다.
- 메시지 읽음/리액션 로직을 Spring `netty-socketio` 기반 Socket.IO 서버로 포팅하고, Map 구조를 직렬화 가능한 형태로 변환 필요.
- AI 스트리밍 이벤트(`aiMessageStart/Chunk/Complete/Error`)를 Spring WebFlux 또는 Reactive Stream으로 재현할 전략이 필요.
- 메시지 저장 시 metadata/mentions/reactions를 JSON 호환 구조로 보존해야 한다.
- 메시지 삭제/편집 기능이 현재 없다. 마이그레이션 범위를 정의하고, soft delete 필드(`isDeleted`) 사용 계획 수립.

## 테스트/검증 노트
- Socket 테스트: `joinRoom` → `fetchPreviousMessages` → `chatMessage` → `messageReaction` → `markMessagesAsRead` 순으로 시나리오 실행.
- REST TODO: Spring 구현 이후 MockMvc로 GET `/message/rooms/:roomId/messages`에 대한 성공/에러 케이스 작성.
- AI: 멘션 포함 메시지 전송 시 스트리밍 이벤트 순서 및 저장 데이터 검증.
- 읽음 처리: 중복 호출 시 `Message.readers`가 중복되지 않는지, 브로드캐스트가 한 번만 전달되는지 확인.

## Open Questions
- REST 메시지 조회가 미구현인데, Spring 마이그레이션 시 Socket 기반 로직을 REST로 치환할지 또는 Socket만 유지할지 결정 필요.
- 파일 메시지 삭제 시 첨부 파일 삭제를 메시지 서비스에서 담당할지 별도 워커에서 처리할지 정의가 필요하다.
- 메시지 리액션 이모지 허용 목록을 제한할지(현재 자유 입력) UX 요구사항을 확인해야 한다.
- 메시지 TTL 또는 보존 정책이 정의되지 않았다. Spring 전환 시 장기 보관 전략을 수립할지 논의 필요.
