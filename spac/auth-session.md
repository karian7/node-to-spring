---
title: 인증 · 세션 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/routes/api/auth.js
  - backend/middleware/auth.js
  - backend/controllers/authController.js
  - backend/services/sessionService.js
  - backend/utils/redisClient.js
  - backend/models/User.js
---

## 기능 개요
- JWT + Redis 기반 단일 세션 정책으로 사용자 인증을 처리하고, 로그인/로그아웃/토큰 갱신 플로우를 제공한다.
- `SessionService`가 사용자별 세션 메타데이터를 Redis에 저장하며, 중복 로그인 감지 시 Socket.IO 경고 및 세션 강제 종료를 수행한다.
- 인증 미들웨어는 모든 보호된 REST/Socket 요청에서 `x-auth-token`과 `x-session-id` 헤더를 반드시 확인하고 세션 유효성 검증 후 `req.user`에 식별자를 주입한다.

## 사용자 시나리오
- **회원가입 즉시 인증**: `/api/auth/register`는 사용자 생성 후 즉시 세션과 JWT를 발급해 클라이언트를 로그인 상태로 만든다.
- **로그인 단일 세션 보장**: `/api/auth/login` 호출 시 기존 활성 세션이 있으면 Socket.IO를 통해 `duplicate_login` 이벤트를 발생시키고, 응답이 없거나 강제 로그인을 선택하면 이전 세션을 종료한다.
- **토큰 검증 및 갱신**: 클라이언트는 주기적으로 `/api/auth/verify-token`으로 토큰/세션을 확인하고, 만료 임박 시 `/api/auth/refresh-token`으로 새 토큰과 세션을 발급받는다.
- **로그아웃**: 보호된 API에서 `/api/auth/logout` 호출 시 세션 키를 제거하고 연결된 소켓에 `session_ended`를 전달해 클라이언트를 종료하도록 안내한다.

## HTTP/Socket 인터페이스
| 타입 | 메서드 | 경로 | 인증 | 요약 |
| --- | --- | --- | --- | --- |
| HTTP | GET | `/api/auth/` | 불필요 | 상태 점검용 JSON(지원 엔드포인트 나열)
| HTTP | POST | `/api/auth/register` | 불필요 | 신규 사용자 생성 (이름∙이메일∙비밀번호 검증, 중복 체크)
| HTTP | POST | `/api/auth/login` | 불필요 | 자격 증명 확인 → 세션 생성 → JWT 발급, 중복 로그인 처리
| HTTP | POST | `/api/auth/logout` | `auth` 미들웨어 | `x-session-id`로 식별되는 세션 삭제, 소켓 통지 시도
| HTTP | POST | `/api/auth/verify-token` | 불필요 (헤더 필요) | 헤더 JWT/세션 ID 일치 여부, Redis 세션 검증, 사용자 정보 반환
| HTTP | POST | `/api/auth/refresh-token` | `auth` 미들웨어 | 기존 세션 제거 후 새 세션/토큰 발급
| Socket | handshake | `token`, `sessionId` | 필수 | JWT 검증 후 `SessionService.validateSession`으로 세션 확인, `connectedUsers` 맵 갱신
| Socket | event | `duplicate_login` | 서버→클라이언트 | 새로운 로그인 시 기존 소켓에 중복 로그인 경고
| Socket | event | `force_login` / `keep_existing_session` | 클라이언트→서버 | 60초 안에 기존/신규 세션 선택, 미응답 시 신규 세션이 기존을 종료
| Socket | event | `session_ended` | 서버→클라이언트 | 로그아웃/중복 로그인/세션 만료 시 통지

> Socket 이벤트 처리 흐름과 세션 종료 브로드캐스트 절차는 [`realtime-transport.md`](realtime-transport.md)에서 상세히 다룹니다.

**요청/응답 요약**
- 로그인 성공 시 응답 본문: `{ success, token, sessionId, user }`. 응답 헤더: `Authorization: Bearer <token>`, `x-session-id: <sessionId>`.
- 모든 보호된 요청은 헤더 `x-auth-token`, `x-session-id`를 요구하며, 미들웨어가 `req.user.id`를 세팅하고 실패 시 401 반환.
- 토큰 검증 성공 시 사용자 정보 반환, 필요 시 `X-Profile-Update-Required: true` 헤더 세팅 (현재 Node 구현에선 `validationResult.needsProfileRefresh`가 미정의 상태).

### HTTP 요청 및 응답 스키마

#### GET /api/auth/
- **Response 200**
```json
{
  "status": "active",
  "routes": {
    "/register": "POST - 새 사용자 등록",
    "/login": "POST - 사용자 로그인",
    "/logout": "POST - 로그아웃 (인증 필요)",
    "/verify-token": "GET - 토큰 검증",
    "/refresh-token": "POST - 토큰 갱신 (인증 필요)"
  }
}
```

#### POST /api/auth/register
- **Request Headers**: 없음 (JSON 기본)
- **Request Body**
```json
{
  "name": "홍길동",
  "email": "user@example.com",
  "password": "string (>=6)"
}
```
- **Response 201**
```json
{
  "success": true,
  "message": "회원가입이 완료되었습니다.",
  "token": "jwt-string",
  "sessionId": "hex-session-id",
  "user": {
    "_id": "6560...",
    "name": "홍길동",
    "email": "user@example.com"
  }
}
```
- **Error 400/409**: `{ "success": false, "message": "..." }` 또는 `{ "success": false, "errors": [{ "field": "email", "message": "올바른 이메일 형식이 아닙니다." }] }`

#### POST /api/auth/login
- **Request Body**
```json
{
  "email": "user@example.com",
  "password": "plaintext"
}
```
- **Response 200**: register 성공 응답과 동일 구조
- **Error 401**: `{ "success": false, "message": "이메일 또는 비밀번호가 올바르지 않습니다." }`
- **Error 409 (중복 로그인)**
```json
{
  "success": false,
  "code": "DUPLICATE_LOGIN_TIMEOUT",
  "message": "중복 로그인 요청이 시간 초과되었습니다."
}
```

#### POST /api/auth/logout
- **Request Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "message": "로그아웃되었습니다."
}
```
- **Error 400**: `{ "success": false, "message": "세션 정보가 없습니다." }`

#### POST /api/auth/verify-token
- **Request Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "user": {
    "_id": "6560...",
    "name": "홍길동",
    "email": "user@example.com",
    "profileImage": ""
  }
}
```
- **Error 401**: `{ "success": false, "message": "세션 정보가 일치하지 않습니다." }`

#### POST /api/auth/refresh-token
- **Request Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "message": "토큰이 갱신되었습니다.",
  "token": "new-jwt",
  "sessionId": "new-session-id",
  "user": {
    "_id": "6560...",
    "name": "홍길동",
    "email": "user@example.com",
    "profileImage": ""
  }
}
```
- **Error 400**: `{ "success": false, "message": "세션 정보가 없습니다." }`

### Socket 이벤트 페이로드
- **Handshake Auth**: `token`, `sessionId` (Socket.IO `auth` payload)
- **duplicate_login (server → client)**
```json
{
  "type": "new_login_attempt",
  "deviceInfo": "Mozilla/5.0 ...",
  "ipAddress": "203.0.113.10",
  "timestamp": 1698656400000
}
```
- **session_ended (server → client)**
```json
{
  "reason": "logout" | "duplicate_login" | "duplicate_login_timeout",
  "message": "로그아웃되었습니다."
}
```
- **force_login / keep_existing_session (client → server)**: `force_login` 시 `{ "token": "existing-session-token" }` 형태로 로그인 유지 여부를 전달 (Node 구현은 토큰 검증 로직이 미구현 상태이므로 Spring 이식 시 명확화 필요).

### 예외 응답 스키마
- **회원가입 (400 - 필드 누락/형식 오류)**
```json
{
  "success": false,
  "message": "모든 필드를 입력해주세요."
}
```
또는
```json
{
  "success": false,
  "message": "입력값이 올바르지 않습니다.",
  "errors": ["이름은 필수입니다.", "비밀번호는 6자 이상이어야 합니다."]
}
```
- **회원가입 (409 - 이메일 중복)**
```json
{
  "success": false,
  "message": "이미 등록된 이메일입니다."
}
```
- **로그인 (400 - 필드 누락)**
```json
{
  "success": false,
  "message": "이메일과 비밀번호를 입력해주세요."
}
```
- **로그인 (401 - 잘못된 자격 증명)**
```json
{
  "success": false,
  "message": "이메일 또는 비밀번호가 올바르지 않습니다."
}
```
- **로그인 (409 - 중복 로그인)**
```json
{
  "success": false,
  "code": "DUPLICATE_LOGIN_TIMEOUT" | "DUPLICATE_LOGIN_REJECTED",
  "message": "중복 로그인 요청이 시간 초과되었습니다."
}
```
- **토큰 검증 (401 - 세션 불일치/만료)**
```json
{
  "success": false,
  "code": "INVALID_SESSION" | "SESSION_NOT_FOUND" | "SESSION_EXPIRED",
  "message": "세션 정보가 일치하지 않습니다."
}
```
- **토큰 갱신/로그아웃 (400 - 세션 헤더 누락)**
```json
{
  "success": false,
  "message": "세션 정보가 없습니다."
}
```
- **공통 내부 오류 (500)**
```json
{
  "success": false,
  "message": "로그인 처리 중 오류가 발생했습니다.",
  "code": "UNKNOWN_ERROR"
}
```
- **Socket 인증 실패**: Handshake 중 `next(new Error('Authentication failed'))` → 클라이언트에서 `connect_error` 이벤트로 `{ message: "Authentication failed" }` 수신.

## 데이터 모델
- **User (MongoDB)**
  - 필드: `name`, `email`(고유), `encryptedEmail`(AES-256-CBC, `encryptionKey` 사용), `password`(bcrypt 해시, select=false), `profileImage`, `createdAt`, `lastActive`.
  - 메서드: `matchPassword`, `updateLastActive`, `changePassword`, `deleteAccount` 등.
- **세션 레코드 (Redis)**
  - 키: `session:<userId>` (JSON), `sessionId:<sessionId>` → userId, `user_sessions:<userId>` → sessionId, `active_session:<userId>` → sessionId.
  - 값 구조: `{ userId, sessionId, createdAt, lastActivity, metadata: { userAgent, ipAddress, deviceInfo, ... } }`.
  - TTL: 24시간 (`SESSION_TTL`), `validateSession` 또는 `updateLastActivity` 호출 시 TTL 갱신.
- **JWT 페이로드**: `{ user: { id }, sessionId, iat }`를 HS256 서명 (`jwtSecret`, 만료 24h).

## 비즈니스 규칙
- 사용자당 활성 세션 1개만 허용: `createSession` 호출 전 `removeAllUserSessions`로 기존 세션 제거.
- 로그인 실패 메시지는 자격 증명 모호성을 유지(잘못된 이메일/비밀번호 모두 401).
- 토큰 검증: JWT 내 `sessionId`와 헤더의 `x-session-id`가 반드시 일치해야 하며, Redis `active_session` 키와도 일치해야 한다.
- 세션 만료: `lastActivity`가 24시간 초과 시 `SESSION_EXPIRED` 처리 후 Redis 키 제거.
- 중복 로그인 감지: Redis에 저장된 세션 외에도 Socket `connectedUsers` 맵으로 실시간 연결을 추적한다.
- 로그아웃 시 쿠키 `token`, `sessionId` 제거 (현재 Node 구현에서만 적용).

## 에러/예외
| 엔드포인트 | 조건 | 상태 코드 | 메시지/코드 |
| --- | --- | --- | --- |
| Register | 필드 누락/형식 오류 | 400 | `errors` 배열 (필드별 메시지)
| Register | 이메일 중복 | 409 | `이미 등록된 이메일입니다.`
| Login | 잘못된 자격 증명 | 401 | `이메일 또는 비밀번호가 올바르지 않습니다.`
| Login | 중복 로그인 응답 미수신 | 409 | `DUPLICATE_LOGIN_TIMEOUT`
| Login | 기존 세션 유지 선택 | 409 | `DUPLICATE_LOGIN_REJECTED`
| Middleware/Auth | 토큰 누락/세션 누락 | 401 | `인증 토큰이 없습니다.`
| Middleware/Auth | JWT 만료 | 401 | `토큰이 만료되었습니다.` (`code` 없음)
| Middleware/Auth | 세션 검증 실패 | 401 | 코드: `INVALID_SESSION`, `SESSION_NOT_FOUND`, `SESSION_EXPIRED`, `UPDATE_FAILED` 등
| Verify Token | JWT/세션 불일치 | 401 | `세션 정보가 일치하지 않습니다.`
| Refresh Token | 세션 헤더 없음 | 400 | `세션 정보가 없습니다.`
| Logout | 세션 헤더 없음 | 400 | `세션 정보가 없습니다.`
| 공통 | 내부 오류 | 500 | `로그인 처리 중 오류가 발생했습니다.` 등, `code`는 상황별(`UNKNOWN_ERROR`)로 부여

## 외부 의존성
- **환경 변수**: `JWT_SECRET`, `REDIS_HOST`, `REDIS_PORT`, `MONGO_URI`, `ENCRYPTION_KEY`, `PASSWORD_SALT` (없을 경우 개발용 기본값 사용).
- **Redis**: 세션/중복 로그인 제어 저장소. 설정 누락 시 `MockRedisClient`로 메모리 대체 (프로세스 재시작 시 세션 유실 위험).
- **Socket.IO**: 로그인 중복 제어, 세션 만료 알림 전파에 필수.

## 마이그레이션 메모
- Spring 전환 시 Redis 단일 세션 정책을 동일하게 구현하고, 세션 TTL 및 `lastActivity` 갱신 방식을 재현해야 한다.
- Socket 기반 중복 로그인 UX 유지 여부 결정 필요. 유지한다면 Spring에서도 `netty-socketio` 어댑터를 사용해 Socket.IO 이벤트(`duplicate_login`, `session_ended`, 선택 응답)를 동일하게 재현한다.
- `SessionService.refreshSession`, `SessionService.getSocketId`는 Node 코드에 선언이 없어 동작하지 않는다. Spring 구현 시 명확한 세션 갱신·소켓 매핑 전략을 확정하고 Node 측 버그 여부를 검증해야 한다.
- 토큰 검증 엔드포인트가 POST로 구현되어 있으나 주석은 GET을 말한다. 인터페이스 확정 후 문서/클라이언트 동기화 필요.
- Redis Mock 사용 시 세션 강제 해제/중복 로그인 제어가 프로세스 메모리에만 남는다. 운영 환경에서는 반드시 실 Redis를 사용하도록 환경 구성.

## 테스트/검증 노트
- REST: Postman 컬렉션으로 회원가입 → 로그인 → 보호 라우트 호출 → 토큰 검증 → 로그아웃 플로우 검증. 헤더 누락/만료 토큰 케이스 포함.
- 세션 강제 종료: 동일 계정으로 두 번째 로그인 시 첫 번째 클라이언트가 `duplicate_login` 이벤트를 수신하고 선택에 따라 응답하는지 확인.
- Refresh: 만료 직전 토큰 시나리오 (JWT 만료 시간을 단축하거나 시뮬레이션하여) 갱신 성공/실패 케이스를 검증.
- Redis 비가용성: 실제 Redis 종료 후 Mock로 전환되는 로그와 세션 지속성을 확인 (운영 모드에서는 실패로 처리할 것인지 결정 필요).
- 통합 테스트: Spring 마이그레이션 시 MockMvc + 임베디드 Redis 대체 라이브러리 (예: Embedded Redis/Testcontainers)로 단일 세션 정책을 자동화.

## Open Questions
- `SessionService.refreshSession`, `SessionService.getSocketId`가 존재하지 않아 현재 Node 버전에서 토큰 검증 갱신과 WebSocket 통지가 정상 동작하는지 확인 필요.
- `/api/auth/verify-token`가 POST로 구현되어 있으나 주석/엔드포인트 설명은 GET을 가리킨다. 실제 클라이언트는 어떤 메서드를 사용하는지 확인 후 일관성 확보 필요.
- Redis Mock 사용 시 프로세스 간 세션 공유가 되지 않는데, 개발/테스트 환경에서 허용되는지와 Spring 이식 시 동일 전략을 쓸지 결정 필요.
- 중복 로그인 승인/거부 이벤트(`force_login`, `keep_existing_session`)를 클라이언트가 실제 구현했는지, Spring 전환 시 UX를 단순화할지 검토해야 한다.
