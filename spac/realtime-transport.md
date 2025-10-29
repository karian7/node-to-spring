---
title: 실시간 전송 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/sockets/chat.js
  - backend/services/sessionService.js
  - backend/models/{User,Room,Message,File}
  - backend/utils/redisClient.js
  - docs/socketio-handler-sync-plan.md
---

## 기능 개요
- Socket.IO 기반으로 인증, 방 참가/퇴장, 메시지 스트림, AI 응답, 읽음·리액션 상태를 전송한다.
- 각 소켓 연결은 JWT(`token`)와 Redis 세션(`sessionId`)을 핸드셰이크에서 검증하며, 중복 로그인 시 이전 소켓을 강제 종료한다.
- 연결 시 사용자-소켓 매핑, 방 참여 상태, 메시지 로딩 큐, AI 스트리밍 세션 등을 인메모리로 관리한다.

## 연결/세션 흐름
1. **Handshake**: 클라이언트가 `auth: { token, sessionId }`로 연결 시도 → JWT 검증 → `SessionService.validateSession` → `socket.user`에 id/name/email/sessionId 저장.
2. **중복 로그인 처리**:
   - 동일 사용자에 대한 기존 소켓이 있으면 `duplicate_login` 이벤트 송신 후 10초 대기.
   - 타임아웃 또는 강제 종료 시 기존 소켓에 `session_ended` 후 disconnect.
   - `connectedUsers` Map을 최신 상태로 유지한다.
3. **세션 연장**: 메시지 전송 등 주요 이벤트마다 `SessionService.updateLastActivity` 호출.
4. **force_login 이벤트**: 클라이언트가 새 토큰을 보내면 현재 소켓을 `session_ended` 후 강제 disconnect.

> JWT·세션 정책 및 중복 로그인 제어 전반은 [`auth-session.md`](auth-session.md)를 참고하세요.

## 이벤트 카탈로그
| 이벤트 | 방향 | 페이로드 | 설명 |
| --- | --- | --- | --- |
| `duplicate_login` | server→client | `{ type, deviceInfo, ipAddress, timestamp }` | 새 로그인 감지 시 기존 기기에 경고 |
| `session_ended` | server→client | `{ reason, message }` | 중복 로그인/force logout 등 세션 종료 통지 |
| `joinRoom` | client→server | `roomId` | 방 참가 요청, 참가자/메시지 로딩 포함 |
| `joinRoomSuccess` | server→client | `{ roomId, participants, messages, hasMore, oldestTimestamp, activeStreams }` | 입장 성공 + 초기 메시지 |
| `joinRoomError` | server→client | `{ message }` | 입장 실패 알림 |
| `leaveRoom` | client→server | `roomId` | 방 퇴장 처리, 시스템 메시지 브로드캐스트 |
| `userLeft` | server→room | `{ userId, name }` | 참여자가 떠났음을 알림 |
| `disconnect` | server→client | Socket.IO 기본 | 서버는 이유에 따라 시스템 메시지/participantsUpdate 브로드캐스트 |
| `chatMessage` | client→server | `{ room, type, content?, fileData? }` | 텍스트/파일 전송, 세션 검증 포함 |
| `message` | server→room | Message 객체 | 새 메시지/시스템 메시지 전파 |
| `fetchPreviousMessages` | client→server | `{ roomId, before? }` | 메시지 페이징 로드 |
| `previousMessagesLoaded` | server→client | `{ messages, hasMore, oldestTimestamp }` | 배치 결과 |
| `messageLoadStart` | server→client | none | 메시지 로딩 시작 신호 |
| `messagesRead` | server→room | `{ userId, messageIds }` | 읽음 상태 전파 |
| `messageReaction` | client→server | `{ messageId, reaction, type }` | 리액션 추가/제거 |
| `messageReactionUpdate` | server→room | `{ messageId, reactions }` | 리액션 상태 공유 |
| `aiMessageStart` | server→room | `{ messageId, aiType, timestamp }` | AI 스트리밍 시작 |
| `aiMessageChunk` | server→room | `{ messageId, currentChunk, fullContent, isCodeBlock, aiType, timestamp, isComplete:false }` | 스트리밍 조각 |
| `aiMessageComplete` | server→room | `{ messageId, _id, content, aiType, timestamp, reactions:{} }` | AI 메시지 최종 완료 |
| `aiMessageError` | server→room | `{ messageId, error, aiType }` | AI 생성 실패 |
| `markMessagesAsRead` | client→server | `{ roomId, messageIds }` | 읽음 처리 요청 |
| `error` | server→client | `{ code?, message }` | 다양한 오류 응답 (권한, 세션 만료 등) |

## 상태 관리 구조
- `connectedUsers: Map<userId, socketId>`
- `userRooms: Map<userId, roomId>`
- `messageQueues: Map<roomId:userId, boolean>` (중복 메시지 로드 방지)
- `messageLoadRetries: Map<roomId:userId, count>` (재시도 횟수)
- `streamingSessions: Map<messageId, { room, aiType, content, lastUpdate, reactions }>`

## 인증 및 보안 규칙
- Handshake 실패 원인: JWT 만료(`Token expired`), 잘못된 토큰(`Invalid token`), 세션 검증 실패(`Invalid session`). → connection error로 전달.
- 각 이벤트 실행 전 `socket.user` 존재 여부, 방 접근 권한, 세션 유효성을 검사한다.
- `chatMessage` 전에는 `SessionService.validateSession` 재호출로 세션 탈취/만료를 억제한다.
- 파일 메시지는 업로드 사용자만 전송 가능하며, 없는 파일/권한 없음 시 오류.
- 중복 로그인 처리 지연(10초) 후 기존 소켓을 강제 종료한다.

## 메시지 로딩/재시도 정책
- 기본 배치 크기: 30, 지연: 300ms (큐 해제용), 재시도 최대 3회, 지수 백오프(2초~10초), 타임아웃 10초.
- 로딩 중인 요청은 `messageQueues`에 키 저장 → 중복 요청 무시.
- 성공 시 FIFO로 정렬된 메시지, `hasMore`, `oldestTimestamp` 반환.
- 실패 시 `error` 이벤트 `type: 'LOAD_ERROR'` 전송.

## AI 스트리밍 플로우
1. `chatMessage`에 AI 멘션 포함 → `extractAIMentions`로 중복 제거.
2. 각 멘션마다 `handleAIResponse` 실행 → `streamingSessions`에 상태 저장.
3. `aiMessageStart` → `aiMessageChunk` (fullContent 누적 포함) → `aiMessageComplete`.
4. 오류 발생 시 `aiMessageError`; 스트리밍 세션을 정리.
5. 최종 메시지는 DB에 `type: 'ai'`, `metadata.query`, `metadata.generationTime` 등 저장.

## 예외/오류 스키마
- **Handshake 실패**: 연결 거부, 클라이언트 `connect_error: { message: 'Authentication failed' }` 등 수신.
- **중복 로그인**: 기존 소켓 `duplicate_login` → 10초 후 `session_ended` + disconnect.
- **세션 만료**: `chatMessage` 등에서 `socket.emit('error', { message: '세션이 만료되었습니다. 다시 로그인해주세요.' })`.
- **권한 없음**: 방 접근 불가 시 `error` `{ message: '채팅방 접근 권한이 없습니다.' }`.
- **메시지 오류**: `{ code:'MESSAGE_ERROR', message:'메시지 전송 중 오류...' }`.
- **읽음/리액션 오류**: 단순 `{ message: '읽음 상태 업데이트 중 오류가 발생했습니다.' }`, `{ message: '리액션 처리 중 오류...' }`.

## 데이터 모델 상호작용
- **SessionService**: Redis 키(`active_session`, `session:<userId>` 등) 기반 세션 단일성 검증/갱신.
- **Room**: 참가자 목록 관리(`$addToSet`, `$pull`), populate로 이름/이메일/프로필 제공.
- **Message**: 시스템 메시지 생성, AI/파일/텍스트 저장, 읽음/리액션 메서드 사용.
- **File**: 파일 메시지 전송 시 metadata와 함께 resolve.
- **User**: 연결 사용자 정보 조회, 참가자 목록 브로드캐스트.

## 비즈니스 규칙
- 한 사용자당 소켓 1개만 활성 상태로 유지 (`connectedUsers`).
- 중복 로그인 대응 타임아웃 10초, 응답 없으면 기존 세션 강제 종료.
- 방 이동 시 이전 방을 떠나고 `userLeft`/시스템 메시지를 전송.
- `leaveRoom`/`disconnect` 시 시스템 메시지를 생성해 잔여 사용자에게 상태 공유.
- 세션 오류/권한 오류 발생 시 클라이언트에 명시적 메시지를 보내 재로그인 유도.
- 메시지 로딩 중에는 추가 요청을 무시해 DB 부하를 완화한다.

## 마이그레이션 메모
- Spring `netty-socketio` 서버로 포팅 시 Socket.IO 이벤트명을 동일하게 유지하고 룸 네임스페이스(`room-list`, `roomId`)를 매핑해야 한다.
- Redis 세션 검증 로직을 Spring Security/Spring Session으로 재구현하고, 중복 로그인 타이머(10초), `duplicate_login` UX를 유지할지 결정 필요.
- 인메모리 상태(Map들)를 분산 환경에서 공유하려면 Redis나 외부 스토어를 사용하거나 sticky session을 요구할지 검토 필요.
- AI 스트리밍은 Node에서 HTTP SSE/Socket을 혼합하므로, Spring에선 WebFlux + Reactor 또는 WebSocket 메시지 브로커로 재현해야 한다.
- 오류 응답 형식이 문자열 위주이므로, Spring 포팅 시 공통 에러 코드 체계를 도입해 API/WS 간 일관성을 확보해야 한다.

## 테스트/검증 노트
- 다중 디바이스 로그인 시나리오: 기존 소켓에서 `duplicate_login` → `session_ended` 수신 후 disconnect 되는지 확인.
- 세션 만료 테스트: Redis에서 세션 무효화 후 메시지 전송 시 오류 응답이 내려오는지 검증.
- 방 이동 시 시스템 메시지/participantsUpdate가 즉시 전파되는지 테스트.
- 메시지 로드 재시도: DB 조회 실패/타임아웃을 모킹해 재시도 및 `LOAD_ERROR` 이벤트를 확인.
- AI 스트리밍: 멘션 포함 메시지 전송 후 스트리밍 이벤트 순서/최종 저장 여부 확인.

## Open Questions
- 인메모리 상태(Map)를 멀티 인스턴스에서 공유할 전략이 필요한가? (예: Redis pub/sub, 외부 스토어)
- 중복 로그인 타임아웃(10초)을 UX 요구에 맞게 조정할 계획이 있는가? 즉시 강제 종료 VS 사용자 선택.
- 오류 이벤트가 문자열 위주인데, 공통 에러 코드/국제화가 필요한가?
- 방 삭제/나가기 REST API와 소켓 이벤트 동기화를 어떻게 유지할지 정책 필요.
