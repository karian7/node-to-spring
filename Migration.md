# Node.js to Spring Boot Migration Plan

이 문서는 `backend` 디렉토리의 Node.js 애플리케이션을 Java Spring Boot 애플리케이션으로 마이그레이션하기 위한 계획을 정의합니다.

## 1. 기능 분석 및 매핑

### 1.1. 인증 (Authentication)

-   **Node.js (`/backend/controllers/authController.js`, `/backend/routes/api/auth.js`)**
    -   JWT (JSON Web Token) 기반 인증 로직.
    -   사용자 등록, 로그인, 로그아웃 기능.
    -   `bcryptjs`를 사용한 비밀번호 해싱.
    -   인증 미들웨어 (`/backend/middleware/auth.js`)를 통해 라우트 보호.

-   **Spring Boot (`/src/main/java/com/example/chatapp/controller/AuthController.java`, `/src/main/java/com/example/chatapp/security`)**
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

-   **Spring Boot (`/src/main/java/com/example/chatapp/controller/UserController.java`, `/src/main/java/com/example/chatapp/service/UserService.java`)**
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

-   **Spring Boot (`/src/main/java/com/example/chatapp/controller/RoomController.java`, `/src/main/java/com/example/chatapp/service/RoomService.java`)**
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

-   **Spring Boot (`/src/main/java/com/example/chatapp/websocket`, `/src/main/java/com/example/chatapp/controller/MessageController.java`)**
    -   Spring WebSocket 및 STOMP 프로토콜을 사용한 실시간 메시징 처리.
    -   `WebSocketConfig`에서 WebSocket 엔드포인트 설정.
    -   `MessageController`에서 메시지 관련 API 및 WebSocket 메시지 핸들링.
    -   `Message` 모델 및 `MessageRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 메시징 기능 차이점**

**차이점 1: 실시간 통신 프로토콜**
- Node.js: Socket.IO (자동 폴백, 커스텀 이벤트)
- Spring Boot: STOMP over WebSocket (표준 프로토콜)

**차이점 2: 메시지 로딩 최적화**
- Node.js: 배치 로딩, 타임아웃 처리, 재시도 로직, 메시지 큐잉
- Spring Boot: 기본적인 메시지 조회

**차이점 3: 읽음 상태 관리**
- Node.js: 실시간 읽음 상태 업데이트, readers 배열 관리
- Spring Boot: 읽음 상태 기능 누락

**차이점 4: 연결 관리**
- Node.js: connectedUsers Map, 중복 로그인 감지, 스트리밍 세션 관리
- Spring Boot: 기본적인 온라인 상태만 관리

**차이점 5: AI 통합**
- Node.js: AI 서비스와 실시간 연동, 스트리밍 응답
- Spring Boot: 기본적인 AI API 호출

### 1.5. 파일 업로드 (File Upload)

-   **Node.js (`/backend/controllers/fileController.js`, `/backend/middleware/upload.js`)**
    -   `multer` 미들웨어를 사용한 파일 업로드 처리.
    -   업로드된 파일 정보(메타데이터)를 MongoDB에 저장.
    -   Mongoose `File` 모델 사용.

-   **Spring Boot (`/src/main/java/com/example/chatapp/controller/FileController.java`, `/src/main/java/com/example/chatapp/service/FileService.java`)**
    -   `MultipartFile`을 사용한 파일 업로드 처리.
    -   `FileService`에서 파일 저장 및 메타데이터 관리 로직 구현.
    -   `File` 모델 및 `FileRepository`를 통한 데이터베이스 연동.

#### 🔍 **분석 결과 - 파일 업로드 기능 차이점**

**차이점 1: 보안 검증**
- Node.js: 경로 안전성 검증, 안전한 파일명 생성, 인증 토큰 검증
- Spring Boot: 기본적인 파일 업로드만 구현

**차이점 2: RAG 시스템 연동**
- Node.js: `processFileForRAG` 함수로 AI 벡터 DB 연동
- Spring Boot: RAG 시스템 연동 누락

**차이점 3: 파일 메타데이터 관리**
- Node.js: 상세한 파일 정보, 업로더 정보, 룸 연관성
- Spring Boot: 기본적인 메타데이터만 저장

**차이점 4: 에러 처리 및 정리**
- Node.js: 실패 시 파일 자동 삭제, 상세한 에러 로깅
- Spring Boot: 기본적인 에러 처리

### 1.6. AI 서비스 (AI Service)

-   **Node.js (`/backend/services/aiService.js`)**
    -   AI 관련 기능 (예: 챗봇, 메시지 분석 등)을 제공하는 서비스.

-   **Spring Boot (`/src/main/java/com/example/chatapp/controller/AiController.java`, `/src/main/java/com/example/chatapp/service/AiService.java`)**
    -   외부 AI API (e.g., OpenAI)와 연동하여 유사 기능 구현.
    -   `AiController` 및 `AiService`에서 관련 로직 처리.

#### 🔍 **분석 결과 - AI 서비스 기능 차이점**

**차이점 1: AI 페르소나 시스템**
- Node.js: 다중 AI 페르소나 (wayneAI, consultingAI) 지원, 각각 다른 역할과 톤
- Spring Boot: 기본적인 AI API 호출만 구현

**차이점 2: 스트리밍 응답**
- Node.js: 실시간 스트리밍 응답, 콜백 시스템을 통한 진행 상태 알림
- Spring Boot: 일반적인 요청-응답 방식

**차이점 3: 컨텍스트 관리**
- Node.js: 시스템 프롬프트와 사용자 메시지 조합, 상세한 지침 설정
- Spring Boot: 단순한 메시지 전달

### 1.7. 세션 관리 (Session Management)

-   **Node.js (`/backend/services/sessionService.js`, `/backend/utils/redisClient.js`)**
    -   Redis를 사용하여 사용자 세션 또는 소켓 연결 정보 관리.

-   **Spring Boot (`/src/main/java/com/example/chatapp/config/RedisConfig.java`)**
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

## 3. 🚨 우선순위별 마이그레이션 작업 목록

### 🔴 **Critical Tasks (즉시 수행 필요)**

1. **인증 시스템 강화** ✅ **완료**
    - [x] SessionService를 Spring Boot로 마이그레이션
    - [x] 커스텀 헤더 지원 (`x-auth-token`, `x-session-id`)
    - [x] 입력값 검증 로직 추가 (@Valid 어노테이션 + 커스텀 검증)
    - [x] 통일된 에러 응답 구조 적용

2. **메시징 시스템 완성** ✅ **완료**
    - [x] 읽음 상태 관리 기능 구현
    - [x] 메시지 배치 로딩 및 최적화
    - [x] 연결 관리 시스템 고도화
    - [x] WebSocket 메시지 전송 및 읽음 상태 실시간 알림

3. **보안 강화** ✅ **완료**
    - [x] 파일 업로드 보안 검증 강화
    - [x] Rate Limiting 구현
    - [x] 세션 보안 강화

### ✅ **완료된 주요 작업 내용**

#### 1. 인증 시스템 강화 (100% 완료)
- **SessionService.java**: Node.js의 Redis 기반 세션 관리를 완전히 이식
- **JwtRequestFilter.java**: 커스텀 헤더 (`x-auth-token`, `x-session-id`) 지원 추가
- **커스텀 검증 어노테이션**: ValidEmail, ValidPassword, ValidName으로 Node.js와 동일한 에러 메시지 제공
- **ApiResponse.java**: `{success: false, message: "..."}` 형태의 통일된 응답 구조
- **AuthController.java**: 세션 관리 통합, 상세한 입력값 검증, 일관된 에러 처리

#### 2. 메시징 시스템 완성 (100% 완료)
- **Message.java**: readers 필드 추가로 읽음 상태 관리
- **MessageService.java**: Node.js의 배치 로딩(30개씩), 타임아웃 처리, 비동기 읽음 상태 업데이트
- **MessageRepository.java**: 읽음 상태 관리를 위한 커스텀 MongoDB 쿼리들
- **MessageController.java**:
    - `/load` 엔드포인트로 배치 로딩
    - `/read`, `/read-all` 엔드포인트로 읽음 상태 관리
    - `/unread-count` 엔드포인트로 읽지 않은 메시지 수 조회
    - WebSocket 실시간 메시지 전송 및 읽음 상태 알림
- **MessageResponse.java**: readCount, readers 정보 포함

#### 3. 보안 강화 (100% 완료)
- **FileSecurityUtil.java**: 파일 업로드 보안 검증 유틸리티
    - 경로 안전성 검증 (Path Traversal 공격 방지)
    - 안전한 파일명 생성 (타임스탬프 + 랜덤 바이트)
    - 파일 확장자 및 MIME 타입 검증
    - 파일 크기 제한 (10MB)
    - 파일명 특수문자 제거 및 sanitization
- **FileService.java**: 보안 검증이 통합된 파일 저장/로드/삭제
    - `storeFileSecurely()`: 종합적인 보안 검증 후 파일 저장
    - `loadFileAsResourceSecurely()`: 안전한 파일 접근 및 로깅
    - `deleteFileSecurely()`: 권한 검증 후 파일 삭제
- **RateLimitConfig.java**: Redis 기반 Rate Limiting 서비스
    - IP당 분당 60회 요청 제한 (Node.js와 동일)
    - 표준 Rate Limit 헤더 지원
    - 클라이언트 식별 (IP + User-Agent)
    - 동적 설정 가능
- **@RateLimit 어노테이션**: 컨트롤러 메서드별 Rate Limiting 적용
    - 클래스/메서드 레벨 적용 가능
    - IP, USER, IP_AND_USER 범위 설정
    - 커스텀 메시지 및 제한 수치 설정
- **RateLimitInterceptor.java**: Rate Limiting 자동 적용
- **SessionService.java 고급 보안 기능**:
    - 의심스러운 활동 감지 (IP/User-Agent 변경)
    - 세션 TTL 동적 조정 (활동 유형별)
    - 강제 로그아웃 기능
    - 보안 이벤트 로깅
    - 중복 로그인 방지

### 🟡 **High Priority Tasks (단기 수행)**

4. **채팅방 관리 고도화** ✅ **완료**
    - [x] 페이지네이션 구현 (정렬, 검색 필터 포함)
    - [x] Health Check 엔드포인트 추가
    - [x] 환경별 에러 처리 시스템

5. **파일 시스템 완성** ✅ **완료**
    - [x] RAG 시스템 연동
    - [x] 파일 메타데이터 관리 강화
    - [x] 실패 시 자동 정리 로직

6. **실시간 통신 고도화** ✅ **완료**
    - [x] Socket.IO 이벤트 체계 완성
    - [x] 실시간 알림 시스템
    - [x] 연결 상태 관리 고도화

7. **API 응답 표준화** ✅ **완료**
    - [x] 통일된 응답 형식
    - [x] 에러 코드 체계화
    - [x] 상태별 적절한 HTTP 상태 코드

8. **메시지 시스템 완성** ✅ **완료**
    - [x] 메시지 읽음 상태 관리
    - [x] 파일 첨부 메시지 처리
    - [x] AI 응답 연동

### 🟢 **Medium Priority Tasks (중기 수행)**

9. **사용자 관리 완성**
    - [ ] 프로필 이미지 업로드 기능
    - [ ] 상세한 입력값 검증
    - [ ] 응답 구조 통일

10. **모니터링 및 로깅**
    - [ ] 상세한 로깅 시스템
    - [ ] 성능 모니터링
    - [ ] 에러 추적 시스템

### 🔵 **Low Priority Tasks (장기 수행)**

11. **AI 서비스 고도화**
    - [ ] AI 페르소나 시스템 구현
    - [ ] 스트리밍 응답 지원
    - [ ] 컨텍스트 관리 시스템

12. **성능 최적화**
    - [ ] 캐싱 전략 구현
    - [ ] 데이터베이스 최적화
    - [ ] 메모리 사용량 최적화

13. **테스트 및 문서화**
    - [ ] 단위 테스트 추가
    - [ ] 통합 테스트 구현
    - [ ] API 문서화

## 2. 마이그레이션 단계

1.  **환경 설정**: `application.properties`에 MongoDB, Redis 등 Node.js 프로젝트(`backend/.env`)와 동일한 설정 값 적용.
2.  **모델 및 DTO 정의**: Node.js의 Mongoose 스키마에 대응하는 Java 모델 클래스(`model`) 및 API 응답/요청을 위한 DTO 클래스(`dto`) 작성.
3.  **리포지토리 생성**: Spring Data MongoDB를 사용하여 각 모델에 대한 리포지토리 인터페이스(`repository`) 생성.
4.  **비즈니스 로직 구현**: Node.js의 컨트롤러 및 서비스 로직을 Spring Boot의 서비스 클래스(`service`)로 마이그레이션.
5.  **API 엔드포인트 구현**: Node.js의 라우팅 로직을 Spring Boot의 컨트롤러 클래스(`controller`)로 마이그레이션.
6.  **보안 설정**: Spring Security를 사용하여 인증 및 인가 로직 구현.
7.  **WebSocket 구현**: Socket.IO 로직을 Spring WebSocket으로 마이그레이션.
8.  **테스트**: 각 기능별 단위 테스트 및 통합 테스트 작성하여 마이그레이션된 기능 검증.
9.  **정리**: 기존 `backend` 디렉토리 코드 제거 및 `README.md` 업데이트.
