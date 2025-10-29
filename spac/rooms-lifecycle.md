---
title: 채팅방 관리 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/routes/api/rooms.js
  - backend/models/Room.js
  - backend/sockets/chat.js
  - backend/middleware/auth.js
---

## 기능 개요
- 채팅방 상태 점검, 목록 조회, 생성, 단일 조회, 참가를 제공하는 REST 계층이다.
- 모든 비공개 엔드포인트는 JWT + Redis 세션을 검증하는 `auth` 미들웨어를 거친다.
- 방 생성/참가 결과는 Socket.IO를 통해 `roomCreated`, `roomUpdate`, `participantsUpdate` 등 실시간 이벤트로 브로드캐스트된다.
- MongoDB `Room` 스키마는 비밀번호를 선택적으로 허용하며, 저장 전 bcrypt 해시를 적용한다.

## 사용자 시나리오
- **상태 점검**: `/api/rooms/health`에서 MongoDB 연결 상태와 latency, 최근 생성 시간을 확인한다.
- **목록 조회**: 페이지네이션, 검색, 정렬 옵션을 적용해 방 목록을 받아본다. 응답에는 메타데이터와 참가자 요약이 포함된다.
- **방 생성**: 인증된 사용자가 방 이름과 선택적 비밀번호를 설정해 새 방을 만들고, 생성 즉시 본인이 참가자로 등록된다.
- **방 상세 조회**: 특정 방의 정보와 참가자 목록을 조회한다.
- **방 입장**: 필요시 비밀번호를 검증한 뒤 참가자로 추가하고, 소켓으로 참가자 업데이트를 전파한다.
- **실시간 브로드캐스트**: 새 방 생성 시 `roomCreated`, 참가자 변화 시 `roomUpdate`/`participantsUpdate`, 사용자가 다른 방으로 이동하면 `userLeft` 이벤트가 발행된다.

## HTTP/Socket 인터페이스
| 타입 | 메서드/이벤트 | 경로/이벤트명 | 인증 | 요약 |
| --- | --- | --- | --- | --- |
| HTTP | GET | `/api/rooms/health` | 불필요 | Mongo 연결 상태, latency, 최근 활동 시간 응답 |
| HTTP | GET | `/api/rooms` | `auth` + rate limit | 방 목록 + 페이지네이션/정렬/검색 |
| HTTP | POST | `/api/rooms` | `auth` | 새 방 생성, 생성자 자체 참가 |
| HTTP | GET | `/api/rooms/:roomId` | `auth` | 단일 방 상세 조회 |
| HTTP | POST | `/api/rooms/:roomId/join` | `auth` | 방 비밀번호 검증 후 참가자 추가 |
| Socket | event | `roomCreated` (server→`room-list`) | JWT handshake | 새 방 브로드캐스트 |
| Socket | event | `roomUpdate` (server→room) | JWT handshake | 참가자 변경 알림 |
| Socket | event | `participantsUpdate` (server→room) | JWT handshake | 참가자 목록 최신화 |
| Socket | event | `joinRoom` (client→server) | JWT handshake | 소켓 룸 참여 및 시스템 메시지 생성 |
| Socket | event | `joinRoomSuccess` / `joinRoomError` | JWT handshake | 소켓 입장 결과 전송 |
| Socket | event | `userLeft` (server→room) | JWT handshake | 사용자가 방을 떠날 때 알림 |

## 요청/응답 스키마

### GET /api/rooms/health
- **Response 200**
```json
{
  "success": true,
  "timestamp": "2025-10-30T06:00:00.000Z",
  "services": {
    "database": {
      "connected": true,
      "latency": 12
    }
  },
  "lastActivity": "2025-10-30T05:45:00.000Z"
}
```
- **Response 503**
```json
{
  "success": false,
  "error": {
    "message": "서비스 상태 확인에 실패했습니다.",
    "code": "HEALTH_CHECK_FAILED"
  }
}
```

### GET /api/rooms
- **Headers**: `x-auth-token`, `x-session-id`
- **Query**: `page`(>=0), `pageSize`(1~50), `sortField`(`createdAt|name|participantsCount`), `sortOrder`(`asc|desc`), `search`
- **Response 200**
```json
{
  "success": true,
  "data": [
    {
      "_id": "6560...",
      "name": "General",
      "hasPassword": false,
      "creator": {
        "_id": "655f...",
        "name": "관리자",
        "email": "admin@example.com"
      },
      "participants": [
        {
          "_id": "655f...",
          "name": "관리자",
          "email": "admin@example.com"
        }
      ],
      "participantsCount": 1,
      "createdAt": "2025-10-29T09:00:00.000Z",
      "isCreator": true
    }
  ],
  "metadata": {
    "total": 12,
    "page": 0,
    "pageSize": 10,
    "totalPages": 2,
    "hasMore": true,
    "currentCount": 1,
    "sort": {
      "field": "createdAt",
      "order": "desc"
    }
  }
}
```

### POST /api/rooms
- **Headers**: `x-auth-token`, `x-session-id`
- **Request (JSON)**
```json
{
  "name": "프로젝트 방",
  "password": "optional"
}
```
- **Response 201**
```json
{
  "success": true,
  "data": {
    "_id": "6561...",
    "name": "프로젝트 방",
    "hasPassword": true,
    "creator": {
      "_id": "655f...",
      "name": "홍길동",
      "email": "user@example.com"
    },
    "participants": [
      {
        "_id": "655f...",
        "name": "홍길동",
        "email": "user@example.com"
      }
    ],
    "createdAt": "2025-10-30T06:05:00.000Z"
  }
}
```

### GET /api/rooms/:roomId
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "data": {
    "_id": "6561...",
    "name": "프로젝트 방",
    "hasPassword": true,
    "creator": {
      "_id": "655f...",
      "name": "홍길동",
      "email": "user@example.com"
    },
    "participants": [
      {
        "_id": "655f...",
        "name": "홍길동",
        "email": "user@example.com"
      }
    ],
    "createdAt": "2025-10-30T06:05:00.000Z"
  }
}
```

### POST /api/rooms/:roomId/join
- **Headers**: `x-auth-token`, `x-session-id`
- **Request (JSON)**
```json
{
  "password": "room-password"
}
```
- **Response 200**
```json
{
  "success": true,
  "data": {
    "_id": "6561...",
    "name": "프로젝트 방",
    "participants": [
      {
        "_id": "655f...",
        "name": "홍길동",
        "email": "user@example.com"
      },
      {
        "_id": "655e...",
        "name": "김개발",
        "email": "dev@example.com"
      }
    ],
    "hasPassword": true
  }
}
```

### Socket 이벤트 페이로드
- **roomCreated (server → `room-list`)**
```json
{
  "_id": "6561...",
  "name": "프로젝트 방",
  "hasPassword": true,
  "creator": { "_id": "655f...", "name": "홍길동", "email": "user@example.com" },
  "participants": [ { "_id": "655f...", "name": "홍길동", "email": "user@example.com" } ],
  "createdAt": "2025-10-30T06:05:00.000Z"
}
```
- **roomUpdate (server → room)**
```json
{
  "_id": "6561...",
  "name": "프로젝트 방",
  "participants": [ ... ],
  "hasPassword": true
}
```
- **participantsUpdate (server → room)**
```json
[
  { "_id": "655f...", "name": "홍길동", "email": "user@example.com" },
  { "_id": "655e...", "name": "김개발", "email": "dev@example.com" }
]
```
- **joinRoomSuccess (server → client)**
```json
{
  "roomId": "6561...",
  "participants": [ { "_id": "655f...", "name": "홍길동", "email": "user@example.com", "profileImage": "" } ],
  "messages": [ /* 최신 메시지 오름차순 */ ],
  "hasMore": false,
  "oldestTimestamp": "2025-10-30T05:59:00.000Z",
  "activeStreams": []
}
```
- **joinRoomError (server → client)**
```json
{
  "message": "채팅방 입장에 실패했습니다."
}
```
- **userLeft (server → room)**
```json
{
  "userId": "655f...",
  "name": "홍길동"
}
```

## 예외 응답 스키마
- **GET /api/rooms rate limit 초과 (429)**
```json
{
  "success": false,
  "error": {
    "message": "너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.",
    "code": "TOO_MANY_REQUESTS"
  }
}
```
- **POST /api/rooms (400 - 이름 누락)**
```json
{
  "success": false,
  "message": "방 이름은 필수입니다."
}
```
- **POST /api/rooms/:roomId/join (401 - 비밀번호 불일치)**
```json
{
  "success": false,
  "message": "비밀번호가 일치하지 않습니다."
}
```
- **공통 404 (방 없음)**
```json
{
  "success": false,
  "message": "채팅방을 찾을 수 없습니다."
}
```
- **GET /api/rooms 실패 (500)**
```json
{
  "success": false,
  "error": {
    "message": "채팅방 목록을 불러오는데 실패했습니다.",
    "code": "ROOMS_FETCH_ERROR"
  }
}
```
- **기타 서버 오류 (500)**
```json
{
  "success": false,
  "message": "서버 에러가 발생했습니다.",
  "error": "stack or detail(optional)"
}
```

## 데이터 모델
- **Room (MongoDB)**
  - 필드: `name`(필수, trim), `creator`(User 참조), `hasPassword`(Boolean), `password`(select:false, bcrypt 해시), `createdAt`, `participants`(User 배열).
  - pre-save 훅: `password` 수정 시 bcrypt 해시 적용, 값이 없으면 `hasPassword=false` 유지.
  - 메서드: `checkPassword(password)` → bcrypt 비교, 비밀번호 없으면 `true`.
- **Rate Limit 정책**
  - window: 60초, max: 60 요청/IP, 초과 시 429 응답 + `TOO_MANY_REQUESTS` 코드.
- **Socket 상태 저장** (chat.js)
  - `connectedUsers`, `userRooms`, `streamingSessions` 등을 사용해 입장 상태와 스트리밍 메시지를 추적.

## 비즈니스 규칙
- 방 이름은 공백 제거 후 저장하며 빈 문자열이면 거부한다.
- 생성자는 자동으로 참가자 배열에 추가된다.
- 페이지네이션 `pageSize`는 1~50 사이로 강제, `page`는 음수 불허.
- 정렬 가능한 필드는 `createdAt`, `name`, `participantsCount`로 제한된다.
- 목록 응답은 참가자 정보를 정규화하고 `isCreator` 플래그를 추가한다.
- 방 비밀번호가 있으면 `Room.hasPassword=true`가 유지되고, join 시 `checkPassword`로 검증한다.
- REST 호출 후 Socket.IO가 `roomCreated`/`roomUpdate`를 발행하여 UI를 동기화한다.
- 소켓 `joinRoom`은 MongoDB에 `$addToSet`으로 참가자를 추가하고 시스템 메시지를 생성해 브로드캐스트한다.

## 에러/예외
| 엔드포인트/이벤트 | 조건 | 상태/처리 | 응답 |
| --- | --- | --- | --- |
| GET `/rooms/health` | Mongo 연결 실패 | 503 | `HEALTH_CHECK_FAILED` 코드 포함 |
| GET `/rooms` | rate limit 초과 | 429 | `TOO_MANY_REQUESTS` |
| GET `/rooms` | DB 조회 실패 | 500 | `ROOMS_FETCH_ERROR` |
| POST `/rooms` | 이름 누락 | 400 | `방 이름은 필수입니다.` |
| POST `/rooms/:roomId/join` | 방 없음 | 404 | `채팅방을 찾을 수 없습니다.` |
| POST `/rooms/:roomId/join` | 비밀번호 오류 | 401 | `비밀번호가 일치하지 않습니다.` |
| Socket `joinRoom` | 권한 없음/기타 오류 | emit `joinRoomError` | `{ message }` |

## 외부 의존성
- **MongoDB/Mongoose**: Room 컬렉션 CRUD, populate 활용.
- **bcryptjs**: 방 비밀번호 해시 및 검증.
- **express-rate-limit**: 목록 엔드포인트의 요청 제한.
- **Socket.IO**: 실시간 방 이벤트(`roomCreated`, `roomUpdate`, `participantsUpdate`, `userLeft`).

## 마이그레이션 메모
- Spring 전환 시 동일한 rate-limit 전략을 유지하거나 Spring Security/RateLimiter로 대체할지 결정 필요.
- MongoDB populate 결과 구조를 Spring DTO에 맞게 재구성해야 한다 (`creator`, `participants` 중첩).
- `roomCreated` 등 소켓 이벤트 명세를 Spring `netty-socketio` 환경으로 이식할 때 동일 payload를 유지해야 UI 호환 가능.
- `hasPassword`/`password` 동작을 Spring Data 레이어에서 재현하고, 선택적 비밀번호 방에 대한 join 로직을 단일 책임 서비스로 이식한다.
- 목록 응답의 캐시 헤더(`Cache-Control: private, max-age=10`)를 Spring 필터/컨트롤러에서 동일하게 설정할지 검토한다.

## 테스트/검증 노트
- REST: Postman/MockMvc로 방 생성→목록 조회→상세 조회→입장 시나리오를 수행하고 비밀번호 방/비밀번호 없는 방 모두 검증.
- Rate limit: 60초 내 61회 요청 시 429 코드와 `TOO_MANY_REQUESTS` 메시지가 내려오는지 확인.
- Socket: 새 방 생성 시 `roomCreated`를 수신하는지, `joinRoom` 호출 시 `joinRoomSuccess` payload가 메시지/참가자 데이터를 포함하는지 검증.
- 보안: 비밀번호 방의 hash 저장 여부와 join 시 잘못된 비밀번호 케이스를 테스트.

## Open Questions
- `roomCreated` 이벤트는 `room-list` 룸에 가입한 클라이언트만 받는다. Spring 마이그레이션 시 동일한 구독 모델을 어떻게 구성할지 결정해야 한다.
- REST `join`과 Socket `joinRoom` 로직이 중복되는데, Spring 에서는 두 경로를 통합할지(예: REST를 소켓 이벤트로 대체) 여부가 필요하다.
- 방 삭제 기능이 누락되어 있다. Spring 마이그레이션 범위에 방 삭제/나가기 기능을 포함할지 정의해야 한다.
