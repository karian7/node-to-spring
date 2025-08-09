# Chat App Migration Plan: Node.js/Express → Java/Spring Boot

## 개요
이 문서는 `/backend` 디렉토리의 Node.js/Express 기반 채팅 애플리케이션을 `/src` 디렉토리의 Java/Spring Boot 버전으로 포팅하는 실행계획입니다.

## 전체 아키텍처 비교

### Node.js/Express 버전
- **Database**: MongoDB (Mongoose ODM)
- **Authentication**: JWT + bcryptjs
- **Real-time**: Socket.IO
- **Cache**: Redis
- **File Upload**: Multer
- **AI Integration**: Axios를 통한 외부 API 호출

### Java/Spring Boot 버전 (현재 상태)
- **Database**: ✅ MongoDB (Spring Data MongoDB) - **완료**
- **Authentication**: Spring Security + JWT (부분 완료)
- **Real-time**: Spring WebSocket (STOMP) - 기본 설정만
- **Cache**: Spring Data Redis - 기본 설정만
- **File Upload**: Spring MultipartFile - 기본 컨트롤러만
- **AI Integration**: WebFlux WebClient - 기본 컨트롤러만

---

## 1단계: 데이터베이스 모델 마이그레이션

### 현재 상태
✅ **MongoDB로 완전 전환 완료**  
✅ **기본 Entity 구조 Node.js와 일치**  
✅ **User, Room, Message, File 모델 생성됨**  
❌ **일부 고급 기능들이 아직 누락**

### 완료된 작업

#### ✅ 1.1 User 모델 - 기본 구조 완료
- `@Document(collection = "users")` 적용
- 기본 필드들 (name, email, password, profileImage, createdAt, lastActive) 구현
- **누락**: 이메일 암호화 로직, 비밀번호 해싱 메서드

#### ✅ 1.2 Room 모델 - 기본 구조 완료  
- `@Document(collection = "rooms")` 적용
- `participantIds` Set으로 구현
- `creatorId`, `hasPassword` 필드 추가
- **누락**: `isPrivate` 플래그, `lastMessage` 참조

#### ✅ 1.3 Message 모델 - 기본 구조 완료
- `@Document(collection = "messages")` 적용
- `MessageType` enum, `AiType` enum 추가
- `fileId`, `mentions`, `isDeleted` 필드 구현
- **누락**: `isEdited` 플래그, `readBy` 배열

#### ✅ 1.4 File 모델 - 완료
- `@Document(collection = "files")` 적용
- Node.js 버전과 거의 동일한 구조

### 남은 작업

#### 1.5 고급 기능 구현
- **User 모델**: 이메일 암호화/복호화 메서드 추가
- **Room 모델**: `isPrivate` 필드, `lastMessage` 참조 추가
- **Message 모델**: `isEdited` 플래그, `readBy` 리스트 추가
- **모든 모델**: MongoDB 인덱스 설정

---

## 2단계: 인증 시스템 완성

### 현재 상태
✅ 기본 Spring Security 설정 있음  
✅ JWT 유틸리티 있음  
✅ AuthController 기본 구조 있음  
❌ **세션 관리 시스템 누락**  
❌ **토큰 갱신 로직 누락**  
❌ **Node.js의 상세 인증 로직 누락**

### 필요한 작업

#### 2.1 AuthController 완성
- ✅ 기본 구조 있음
- ❌ `POST /api/auth/register` - 사용자 등록 로직 완성
- ❌ `POST /api/auth/login` - 로그인 로직 완성  
- ❌ `POST /api/auth/logout` - 로그아웃 구현
- ❌ `POST /api/auth/verify-token` - 토큰 검증 구현
- ❌ `POST /api/auth/refresh-token` - 토큰 갱신 구현

#### 2.2 세션 관리 시스템 구현
- Redis 기반 세션 저장
- 중복 로그인 방지
- 세션 만료 처리

#### 2.3 비밀번호 및 이메일 암호화
- BCrypt를 이용한 패스워드 해싱
- Node.js crypto 모듈에 대응하는 이메일 암호화 로직

---

## 3단계: WebSocket 실시간 통신 구현

### 현재 상태
✅ 기본 WebSocket 설정 있음  
✅ WebSocketConfig 클래스 있음  
❌ **Socket.IO의 풍부한 실시간 기능들 누락**  
❌ **실시간 메시지 전송/수신 로직 누락**

### 필요한 작업

#### 3.1 WebSocket 설정 완성
- STOMP over WebSocket 설정 완성
- 인증된 사용자만 접속 허용
- CORS 설정 완성

#### 3.2 Socket.IO 대응 기능 구현
- **Node.js 기능들**:
  - 사용자 온라인/오프라인 상태 관리
  - 타이핑 상태 표시 (`typing`, `stop_typing`)
  - 읽음 상태 관리 (`message_read`)
  - 방 입장/퇴장 실시간 알림
  - 메시지 전송/수신 (`send_message`, `receive_message`)
  - AI 스트리밍 응답 (`ai_stream`)

#### 3.3 고급 실시간 기능
- 메시지 배치 로딩
- 중복 로그인 감지 및 처리
- 연결 상태 모니터링

---

## 4단계: 파일 업로드 시스템

### 현재 상태
✅ 기본 FileController 있음  
✅ File 모델 완성됨  
❌ **Multer의 파일 처리 로직 누락**  
❌ **파일 다운로드 기능 누락**

### 필요한 작업

#### 4.1 파일 업로드 처리 완성
- MultipartFile을 이용한 파일 업로드 구현
- Node.js Multer와 동일한 파일 타입 검증
- 파일 크기 제한 (Node.js: 50MB)
- 파일명 중복 방지 로직

#### 4.2 파일 다운로드 구현
- 파일 스트리밍 다운로드
- 권한 확인 후 다운로드
- Content-Type 적절한 설정

---

## 5단계: 방(Room) 관리 시스템

### 현재 상태
✅ 기본 RoomController 있음  
✅ Room 모델 기본 구조 완성  
❌ **Node.js의 복잡한 방 관리 로직 누락**

### 필요한 작업

#### 5.1 방 API 엔드포인트 구현
- `POST /api/rooms` - 방 생성
- `GET /api/rooms` - 방 목록 조회  
- `POST /api/rooms/:roomId/join` - 방 참여
- `POST /api/rooms/:roomId/leave` - 방 나가기
- `GET /api/rooms/:roomId/messages` - 메시지 히스토리

#### 5.2 Node.js 대응 기능 구현
- 비밀번호 보호 방 지원
- 참여자 권한 관리
- 메시지 페이지네이션 (Node.js: BATCH_SIZE = 30)

---

## 6단계: AI 서비스 통합

### 현재 상태
✅ 기본 AiController 있음  
✅ WebFlux 의존성 있음  
❌ **Node.js의 AI 서비스 로직 누락**

### 필요한 작업

#### 6.1 AI 서비스 구현
- WebClient를 이용한 외부 AI API 호출
- Node.js aiService.js의 기능 구현
- 비동기 처리 및 에러 핸들링

#### 6.2 스트리밍 응답 처리
- 실시간 AI 응답 스트리밍 (Node.js의 `ai_stream` 대응)
- WebSocket을 통한 실시간 전송

---

## 7단계: Redis 캐시 시스템

### 현재 상태
✅ Redis 의존성 있음  
✅ RedisConfig 설정 있음  
❌ **실제 캐시 활용 로직 누락**

### 필요한 작업

#### 7.1 Node.js Redis 사용 패턴 구현
- 사용자 세션 정보 저장
- 온라인 상태 관리 (`connectedUsers` Map 대응)
- 방별 참여자 정보 캐시 (`userRooms` Map 대응)

---

## 8단계: 보안 및 미들웨어

### 현재 상태
✅ 기본 Spring Security 설정 있음  
❌ **Node.js의 미들웨어들 대응 누락**

### 필요한 작업

#### 8.1 CORS 설정 완성
- Node.js의 상세한 CORS 옵션 구현
- 허용 도메인, 헤더, 메서드 설정

#### 8.2 기타 보안 설정
- Rate Limiting 구현
- 입력 검증 강화

---

## 9단계: 에러 핸들링 및 로깅

### 필요한 작업

#### 9.1 Node.js 대응 에러 처리
- 전역 에러 핸들러 구현
- Node.js와 동일한 에러 응답 형식

---

## 10단계: 환경 설정 및 테스트

### 현재 상태
✅ MongoDB 연결 설정 완료  
✅ 기본 application.properties 설정됨  
❌ **Node.js 환경변수들 대응 누락**

### 필요한 작업

#### 10.1 환경 설정 완성
- Node.js .env 파일의 모든 설정 대응
- 개발/프로덕션 환경 분리

---

## 🎯 업데이트된 우선순위 및 실행 순서

### 📍 **즉시 시작 가능** (MongoDB 완료로 인한)
1. **2단계 (인증 시스템)** - 데이터베이스 준비됨
2. **5단계 (방 관리)** - Room 모델 준비됨  
3. **4단계 (파일 업로드)** - File 모델 준비됨

### 📍 **다음 단계**
4. **3단계 (WebSocket)** - 핵심 기능
5. **6단계 (AI 서비스)** - 고급 기능
6. **7단계 (Redis 캐시)** - 성능 최적화

### 📍 **마지막 단계**  
7. **8단계 (보안)** → **9단계 (에러)** → **10단계 (테스트)**

**🚀 다음 추천 작업**: "2단계 인증 시스템부터 시작해 주세요" - AuthController 완성 및 세션 관리 구현
