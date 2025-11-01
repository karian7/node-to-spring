# Node.js to Spring Boot Migration Plan

이 문서는 `backend` 디렉토리의 Node.js 애플리케이션을 Java Spring Boot 애플리케이션으로 마이그레이션하기 위한 계획을 정의합니다.

## 0. 스펙 문서 현황
- ✅ [`spac/auth-session.md`](spac/auth-session.md): JWT + Redis 단일 세션, 중복 로그인 제어, force_login 이벤트 흐름 정리
- ✅ [`spac/user-account.md`](spac/user-account.md): 프로필/이미지/회원 탈퇴 API 규격 및 예외 정리
- ✅ [`spac/rooms-lifecycle.md`](spac/rooms-lifecycle.md): 방 REST + Socket.IO 이벤트, rate limit, 헬스 체크 명세
- ✅ [`spac/messages-history.md`](spac/messages-history.md): 메시지 배치 로딩, 읽음/리액션, AI 스트림 연계, REST 미구현 TODO 명시
- ✅ [`spac/realtime-transport.md`](spac/realtime-transport.md): Socket.IO 인증, 중복 로그인, 세션/스트리밍 상태 관리
- ✅ [`spac/file-handling.md`](spac/file-handling.md): 업로드 파이프라인, 권한 검증, 다운로드/미리보기 명세
- ✅ [`spac/ai-integration.md`](spac/ai-integration.md): OpenAI 스트리밍, 페르소나, 에러 처리 흐름
- ✅ [`spac/platform-core.md`](spac/platform-core.md): 서버 부트스트랩, 공통 미들웨어, Redis/환경 변수 구성

> 각 스펙에는 Node 구현 기준 동작과 Spring 마이그레이션 TODO가 포함되어 있으며, 아래 마이그레이션 플랜은 해당 문서를 근거로 업데이트되었습니다.

## 1. 기능 분석 및 매핑

### 1.1. 인증 (Authentication)

-   **Node.js (`/backend/controllers/authController.js`, `/backend/routes/api/auth.js`)**
    -   JWT (JSON Web Token) 기반 인증 로직.
    -   사용자 등록, 로그인, 로그아웃 기능.
    -   `bcryptjs`를 사용한 비밀번호 해싱.
    -   인증 미들웨어 (`/backend/middleware/auth.js`)를 통해 라우트 보호.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/controller/AuthController.java`, `/src/main/java/com/ktb/chatapp/security`)**
    -   Spring Security를 사용한 인증 및 권한 부여.
    -   JWT 토큰 생성, 검증 및 재발급 로직 구현.
    -   `PasswordEncoder`를 사용한 비밀번호 암호화.
    -   `SecurityConfig`에서 경로별 접근 제어 설정.

#### 🔍 **분석 결과 - 인증 기능 차이점**

**차이점 1: 세션 관리 방식**
- Node.js: JWT + Redis 기반 세션 관리 (SessionService 사용)
- Spring Boot: JWT만 사용하며 세션 검증 로직 누락

**차이점 2: 토큰 헤더 방식**
- Node.js: `x-auth-token`, `x-session-id` 커스텀 헤더 사용
- Spring Boot: 표준 `Authorization: Bearer` 헤더 사용

**차이점 3: 입력값 검증**
- Node.js: 컨트롤러에서 직접 검증 (이메일 정규식, 비밀번호 길이 등)
- Spring Boot: 검증 로직 누락

**차이점 4: 에러 응답 구조**
- Node.js: `{success: false, message: "..."}`
- Spring Boot: 다양한 응답 구조 혼재

### 1.2. 사용자 관리 (User Management)

-   **Node.js (`/backend/controllers/userController.js`, `/backend/routes/api/users.js`)**
    -   사용자 정보 조회, 수정 기능.
    -   Mongoose `User` 모델 사용.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/controller/UserController.java`, `/src/main/java/com/ktb/chatapp/service/UserService.java`)**
    -   `UserController`에서 사용자 관련 API 제공.
    -   `UserService`에서 비즈니스 로직 처리.
    -   `User` 모델 및 `UserRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 사용자 관리 기능 차이점**

**차이점 1: 프로필 이미지 처리**
- Node.js: `profileImage` 필드를 통한 이미지 URL 저장, 업로드 로직 포함
- Spring Boot: 기본적인 프로필 조회/수정만 구현

**차이점 2: 입력값 검증**
- Node.js: 상세한 필드별 검증 (이름 길이, 이메일 형식 등)
- Spring Boot: `@Valid` 어노테이션 기반 검증

**차이점 3: 응답 구조 일관성**
- Node.js: `{success: true/false, user: {...}}`
- Spring Boot: 직접 객체 반환

### 1.3. 채팅방 관리 (Room Management)

-   **Node.js (`/backend/routes/api/rooms.js`, `/backend/models/Room.js`)**
    -   채팅방 생성, 조회, 참여 기능.
    -   Mongoose `Room` 모델 사용.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/controller/RoomController.java`, `/src/main/java/com/ktb/chatapp/service/RoomService.java`)**
    -   `RoomController`에서 채팅방 관련 API 제공.
    -   `RoomService`에서 비즈니스 로직 처리.
    -   `Room` 모델 및 `RoomRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 채팅방 관리 기능 차이점**

**차이점 1: 페이지네이션**
- Node.js: 고급 페이지네이션 (page, pageSize, 정렬, 검색 필터)
- Spring Boot: 기본적인 전체 조회만 구현

**차이점 2: Rate Limiting**
- Node.js: `express-rate-limit`을 통한 API 호출 제한
- Spring Boot: Rate Limiting 미구현

**차이점 3: Health Check**
- Node.js: `/health` 엔드포인트로 DB 연결 상태, 지연 시간 체크
- Spring Boot: Health Check 기능 누락

**차이점 4: 에러 처리**
- Node.js: 환경별 상세 에러 정보 제공 (development/production)
- Spring Boot: 기본적인 에러 응답

**차이점 5: 실시간 알림**
- Node.js: Socket.IO를 통한 방 생성/참여 실시간 알림
- Spring Boot: WebSocket 연동 부분적 구현

### 1.4. 메시징 (Messaging)

-   **Node.js (`/backend/sockets/chat.js`, `/backend/routes/api/message.js`)**
    -   Socket.IO를 사용한 실시간 메시지 전송 및 수신.
    -   이전 메시지 조회를 위한 API.
    -   Mongoose `Message` 모델 사용.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/websocket`, `/src/main/java/com/ktb/chatapp/controller/MessageController.java`)**
    -   기존 STOMP 기반 구현 대신 `netty-socketio` + Redisson 환경으로 Socket.IO 호환 실시간 메시징을 구축할 예정.
    -   `SocketIOChatHandler` 등 소켓 처리 레이어에서 이벤트를 관리하고, `/src/main/java/com/ktb/chatapp/controller/MessageController.java`는 REST 메시지 API를 담당한다.
    -   `Message` 모델 및 `MessageRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 메시징 기능 차이점**

**차이점 1: 실시간 통신 프로토콜**
- Node.js: Socket.IO (자동 폴백, 커스텀 이벤트)
- Spring Boot(목표): `netty-socketio` + Redisson 기반 Socket.IO 호환 환경

**차이점 2: 메시지 로딩 최적화**
- Node.js: 배치 로딩, 타임아웃 처리, 재시도 로직, 메시지 큐잉
- Spring Boot: 기본적인 메시지 조회

**차이점 3: 읽음 상태 관리**
- Node.js: 실시간 읽음 상태 업데이트, readers 배열 관리
- Spring Boot: 읽음 상태 기능 누락

**차이점 4: 연결 관리**
- Node.js: connectedUsers Map, 중복 로그인 감지, 스트리밍 세션 관리
- Spring Boot: 기본적인 온라인 상태만 관리

**차이점 5: AI 통합** ✅
- Node.js: AI 서비스와 실시간 연동, 스트리밍 응답
- Spring Boot: ✅ **완료** - OpenAI 스트리밍 연동, 페르소나별 시스템 프롬프트, Socket 이벤트 처리

### 1.5. 파일 업로드 (File Upload)

-   **Node.js (`/backend/controllers/fileController.js`, `/backend/middleware/upload.js`)**
    -   `multer` 미들웨어를 사용한 파일 업로드 처리.
    -   업로드된 파일 정보(메타데이터)를 MongoDB에 저장.
    -   Mongoose `File` 모델 사용.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/controller/FileController.java`, `/src/main/java/com/ktb/chatapp/service/FileService.java`)**
    -   `MultipartFile`을 사용한 파일 업로드 처리.
    -   `FileService`에서 파일 저장 및 메타데이터 관리 로직 구현.
    -   `File` 모델 및 `FileRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 파일 업로드 기능 차이점**

**구현 상태**: ✅ **100% 완료** (2025-10-30)

**차이점 1: 보안 검증** ✅
- Node.js: 경로 안전성 검증, 안전한 파일명 생성, 인증 토큰 검증
- Spring Boot: ✅ **완료** - FileSecurityUtil에 Path Traversal 방어, 파일명 검증, 3단계 권한 검증 구현

**차이점 2: 파일명 생성 방식** ✅
- Node.js: timestamp_randomhex16.ext (예: 1700000000000_abcd1234efgh5678.pdf)
- Spring Boot: ✅ **완료** - Node.js와 동일한 형식으로 변경, NFC 정규화 추가

**차이점 3: 파일 메타데이터 관리** ✅
- Node.js: user, path, uploadDate 필드 사용
- Spring Boot: ✅ **완료** - File 모델 필드 변경 (uploadedBy→user, uploadedAt→uploadDate, path 추가)

**차이점 4: 다운로드 헤더** ✅
- Node.js: UTF-8 인코딩, Cache-Control, Content-Length 포함
- Spring Boot: ✅ **완료** - RFC 5987 UTF-8 인코딩, 적절한 캐시 정책, Content-Length 추가

**차이점 5: 파일 미리보기** ✅
- Node.js: /api/files/view/:filename 엔드포인트 (Content-Disposition: inline)
- Spring Boot: ✅ **완료** - view 엔드포인트 추가, immutable 캐시 정책 적용

**차이점 6: 권한 검증** ✅
- Node.js: 파일 → 메시지 → 방 참가자 3단계 검증
- Spring Boot: ✅ **완료** - loadFileAsResourceSecurely()에 동일한 3단계 검증 구현

**차이점 7: API 엔드포인트** ✅
- Node.js: 4개 엔드포인트 (upload, download, view, delete)
- Spring Boot: ✅ **완료** - Java 전용 엔드포인트 제거 (my-files, room/:roomId, rag-processed)

**구현 완료 상세**: [06-file-handling-implementation.md](docs/implementation-analysis/06-file-handling-implementation.md)

### 1.6. AI 서비스 (AI Service)

-   **Node.js (`/backend/services/aiService.js`)**
    -   AI 관련 기능 (예: 챗봇, 메시지 분석 등)을 제공하는 서비스.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/controller/AiController.java`, `/src/main/java/com/ktb/chatapp/service/AiService.java`)**
    -   외부 AI API (e.g., OpenAI)와 연동하여 유사 기능 구현.
    -   `AiController` 및 `AiService`에서 관련 로직 처리.

#### 🔍 **분석 결과 - AI 서비스 기능 차이점**

**구현 상태**: ✅ **100% 완료** (2025-10-30)

**차이점 1: AI 페르소나 시스템** ✅
- Node.js: 다중 AI 페르소나 (wayneAI, consultingAI) 지원, 각각 다른 역할과 톤
- Spring Boot: ✅ **완료** - AiType enum에 페르소나 상세 정보 구현, getSystemPrompt() 메서드로 시스템 프롬프트 자동 생성

**차이점 2: 스트리밍 응답** ✅
- Node.js: 실시간 스트리밍 응답, 콜백 시스템을 통한 진행 상태 알림
- Spring Boot: ✅ **완료** - AiServiceImpl에 OpenAI SSE 스트리밍 구현, Consumer/Runnable 콜백 지원

**차이점 3: 컨텍스트 관리** ✅
- Node.js: 시스템 프롬프트와 사용자 메시지 조합, 상세한 지침 설정
- Spring Boot: ✅ **완료** - 페르소나별 시스템 프롬프트 자동 적용, model: gpt-4, temperature: 0.7 설정

**차이점 4: Socket 이벤트** ✅
- Node.js: 4개 이벤트 (aiMessageStart, aiMessageChunk, aiMessageComplete, aiMessageError)
- Spring Boot: ✅ **기존 완료** - ChatMessageHandler에 모든 이벤트 구현 완료

**구현된 파일**:
- `src/main/java/com/ktb/chatapp/model/AiType.java` - 페르소나 정의
- `src/main/java/com/ktb/chatapp/service/impl/AiServiceImpl.java` - OpenAI 스트리밍 연동
- `src/main/java/com/ktb/chatapp/websocket/socketio/handler/ChatMessageHandler.java` - Socket 이벤트 처리 (기존 완료)

**상세 분석 문서**: `/docs/implementation-analysis/07-ai-integration.md`

### 1.7. 세션 관리 (Session Management)

-   **Node.js (`/backend/services/sessionService.js`, `/backend/utils/redisClient.js`)**
    -   Redis를 사용하여 사용자 세션 또는 소켓 연결 정보 관리.

-   **Spring Boot (`/src/main/java/com/ktb/chatapp/config/RedisConfig.java`)**
    -   Spring Data Redis를 사용하여 Redis 연동.
    -   `RedisTemplate` 또는 `RedisRepository`를 활용하여 데이터 저장 및 조회.
    -   WebSocket 세션 정보 등을 Redis에 저장하여 관리.

#### 🔍 **분석 결과 - 세션 관리 기능 차이점**

**차이점 1: 세션 데이터 구조**
- Node.js: 복잡한 세션 메타데이터 (userAgent, ipAddress, deviceInfo, createdAt)
- Spring Boot: 기본적인 세션 정보만 관리

**차이점 2: 다중 세션 관리**
- Node.js: 사용자별 여러 세션 관리, 활성 세션 추적
- Spring Boot: 단순한 세션 관리

**차이점 3: 안전성 기능**
- Node.js: JSON 직렬화/파싱 안전성 검증, 에러 처리
- Spring Boot: 기본적인 Redis 연동

**차이점 4: TTL 관리**
- Node.js: 동적 TTL 설정, 세션 연장 기능
- Spring Boot: 기본 TTL 설정

## 3. 우선순위별 마이그레이션 작업 목록

스펙 문서 기준으로 실제 구현 여부를 다시 점검했으며, 아래 체크리스트는 **남은 구현 과제**에 초점을 둡니다. 각 항목 옆에는 참고할 스펙 문서를 명시했습니다.

### 🔴 Critical: 인증/세션, 실시간 기반 기능
1. **인증 시스템 강화** ([`auth-session.md`](spac/auth-session.md), [`platform-core.md`](spac/platform-core.md))
   - [ ] Spring `SessionService`에 Redis 단일 세션 정책 이식 (`SessionService` spec 참고)
   - [ ] `x-auth-token`/`x-session-id` 헤더 파이프라인 구현 및 통합 테스트
   - [ ] force_login UX 결정 및 `netty-socketio` 환경 재현 (세션 종료 이벤트)
   - [ ] 에러 응답/로그 포맷 통일

2. **실시간 전송 계층 구축** ([`realtime-transport.md`](spac/realtime-transport.md), [`auth-session.md`](spac/auth-session.md))
   - [ ] `netty-socketio` + Redisson 기반 Socket.IO 서버 구성
   - [ ] 중복 로그인/세션 종료 이벤트 브로드캐스트 구현
   - [ ] 스트리밍 세션 상태(Map) 분산 대응 전략 확정 (Redis pub/sub 등)

3. **메시지 기록/배치 로딩 구현** ([`messages-history.md`](spac/messages-history.md))
   - [ ] REST `GET /message/rooms/:id/messages` 완성 (현행 500) 및 배치 API 제공
   - [ ] 읽음/리액션 실시간 업데이트 + Redis 세션 검증 통합
   - [ ] AI 스트림 메시지 저장 동기화 (messages-history ↔ ai-integration)

### 🟡 High: 도메인별 API 보강
4. **채팅방 관리 고도화** ([`rooms-lifecycle.md`](spac/rooms-lifecycle.md))
   - [ ] 페이지네이션/검색/정렬 재현, rate limit 적용
   - [ ] `/api/rooms/health` 및 헬스체크 응답 구성
   - [ ] REST join vs Socket join 중복 전략 결정

5. **파일 파이프라인 완성** ([`file-handling.md`](spac/file-handling.md), [`messages-history.md`](spac/messages-history.md))
   - [ ] 안전한 파일명/경로 검증, 업로드 정책 통합
   - [ ] 다운로드/미리보기 권한 체크 + 메시지 연동 테스트
   - [ ] RAG 후처리(`processFileForRAG`) 실행 전략 수립

6. **사용자 계정 정비** ([`user-account.md`](spac/user-account.md))
   - [ ] 프로필 이미지 업로드/삭제 구현 (Multer ↔ Spring Multipart)
   - [ ] 입력값 검증 및 응답 구조 통일
   - [ ] 계정 삭제 시 연관 데이터 정리 전략 수립

### 🟢 Medium: 코어 플랫폼 & 관측성
7. **플랫폼 공통 구성 정리** ([`platform-core.md`](spac/platform-core.md))
   - [ ] 환경 변수/비밀 관리 전략(Vault 등) 결정
   - [ ] 로깅/모니터링 도구 선정 (Winston ↔ Spring Logger 대응)
   - [ ] Redis Mock 개발/테스트 전략 vs 실 Redis 강제

8. **모니터링 및 로깅**
   - [ ] 구조화 로깅 도입
   - [ ] 성능/헬스 모니터링 (Prometheus, Actuator 등)
   - [ ] 에러 추적 (Sentry 등)

### 🔵 Low: AI·성능·테스트 고도화
9. **AI 서비스 고도화** ([`ai-integration.md`](spac/ai-integration.md)) ✅ **완료**
   - [x] OpenAI 스트리밍 구현 완료 (HttpURLConnection 사용)
   - [x] 페르소나 시스템 구현 완료 (AiType enum)
   - [x] Socket 이벤트 연동 완료 (ChatMessageHandler)
   - [ ] 동시 호출 제한 및 타임아웃 정책 (선택사항)

10. **성능 최적화**
    - [ ] 캐싱 전략 (Room/Message 리스트)
    - [ ] MongoDB 인덱스/쿼리 최적화 검증
    - [ ] 메모리/세션 상태 관리 튜닝

11. **테스트 및 문서화**
    - [ ] 단위/통합 테스트 케이스 (Spec 기반 시나리오)
    - [ ] API 문서화 (OpenAPI/Asciidoc)
    - [ ] 마이그레이션 완료 검증 체크리스트

## 2. 마이그레이션 단계

1.  **환경 설정**: `application.properties`에 MongoDB, Redis 등 Node.js 프로젝트(`backend/.env`)와 동일한 설정 값 적용.
2.  **모델 및 DTO 정의**: Node.js의 Mongoose 스키마에 대응하는 Java 모델 클래스(`model`) 및 API 응답/요청을 위한 DTO 클래스(`dto`) 작성.
3.  **리포지토리 생성**: Spring Data MongoDB를 사용하여 각 모델에 대한 리포지토리 인터페이스(`repository`) 생성.
4.  **비즈니스 로직 구현**: Node.js의 컨트롤러 및 서비스 로직을 Spring Boot의 서비스 클래스(`service`)로 마이그레이션.
5.  **API 엔드포인트 구현**: Node.js의 라우팅 로직을 Spring Boot의 컨트롤러 클래스(`controller`)로 마이그레이션.
6.  **보안 설정**: Spring Security를 사용하여 인증 및 인가 로직 구현.
7.  **WebSocket 구현**: Socket.IO 로직을 Spring `netty-socketio` + Redisson 환경으로 마이그레이션.
8.  **테스트**: 각 기능별 단위 테스트 및 통합 테스트 작성하여 마이그레이션된 기능 검증.
9.  **정리**: 기존 `backend` 디렉토리 코드 제거 및 `README.md` 업데이트.
