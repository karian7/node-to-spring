# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 레포지토리의 코드를 작업할 때 참고하는 가이드입니다.

## 프로젝트 개요

Spring Boot 3.5와 Java 21 기반의 실시간 채팅 애플리케이션 백엔드입니다.
- MongoDB를 통한 데이터 영속화
- MongoDB TTL 기반 세션 관리 및 레이트 리밋
- JWT 인증 및 Spring Security
- SocketIO를 통한 실시간 통신
- OpenAI 연동 AI 채팅 기능

상세한 기술 스택 및 환경 설정은 [README.md](README.md)를 참조하세요.

## 개발 명령어

```bash
# 빌드 및 테스트
mvn clean install

# 애플리케이션 실행 (포트 5001)
mvn spring-boot:run

# 테스트 실행
mvn test

# 편의 명령 (Makefile)
make dev      # dev 프로파일로 실행
make build    # 빌드 및 테스트
make test     # 테스트만 실행
```

## 아키텍처

### 핵심 기술
자세한 내용은 [README.md의 주요 기술 스택](README.md#주요-기술-스택) 참조

### 패키지 구조

```
src/main/java/com/ktb/chatapp/
├── controller/     # REST API 엔드포인트
├── service/        # 비즈니스 로직 레이어
├── repository/     # MongoDB 데이터 접근 계층
├── dto/            # 요청/응답 데이터 전송 객체
├── model/          # 엔티티 클래스 (MongoDB 도큐먼트)
├── config/         # 설정 클래스 (Async, RateLimit, WebSocket 등)
├── security/       # JWT 인증 및 인가
├── websocket/      # SocketIO 이벤트 핸들러
├── util/           # 유틸리티 클래스
├── validation/     # 커스텀 검증 로직 및 어노테이션
├── annotation/     # 커스텀 어노테이션
├── event/          # 애플리케이션 이벤트
└── exception/      # 예외 처리 클래스
```

### 주요 컴포넌트

#### 인증 및 세션 관리
- JWT 토큰 기반 인증 (`x-auth-token`, `x-session-id` 헤더)
- MongoDB TTL 인덱스를 활용한 세션 저장소 (IP, User-Agent, 디바이스 정보 포함)
- IP 기반 레이트 리밋 (분당 60 요청)
- 커스텀 검증 어노테이션: `@ValidEmail`, `@ValidPassword`, `@ValidName`

#### 실시간 통신
- SocketIO 서버 (포트 5002, REST API와 분리)
- 이벤트 기반 아키텍처로 채팅 메시지, 타이핑 표시, 방 알림 처리
- 사용자 접속 상태 추적
- 메시지 읽음 상태 실시간 업데이트

#### 파일 시스템
- 경로 순회 공격 방지가 적용된 안전한 파일 업로드
- MongoDB에 파일 메타데이터 저장
- RAG 시스템 연동을 위한 AI 처리
- 업로드 실패 시 자동 정리

#### API 응답 구조
모든 API는 `ApiResponse<T>` 형식의 표준화된 응답 사용:
```json
{
  "success": true/false,
  "message": "...",
  "data": {...}
}
```

## 설정

### 필수 환경 설정
환경 변수 및 의존성 서비스 설정은 [README.md의 환경 변수 설정](README.md#환경-변수-설정) 및 [종속 서비스 실행](README.md#종속-서비스-실행) 참조

### 주요 설정 파일
- `src/main/resources/application.properties` - 메인 설정
- `.env` - 환경 변수 (암호화 키, JWT 시크릿, OpenAI API 키 등)
- `docker-compose.yml` - MongoDB 컨테이너 설정 (Redis 서비스는 현재 비활성화 상태로 유지)

## 개발 가이드라인

### 코드 작성 규칙
1. **DTO 패턴 준수**: 요청/응답은 반드시 DTO 클래스 사용
2. **서비스 레이어 분리**: 비즈니스 로직은 Service 클래스에 구현
3. **표준 응답 형식**: 모든 API는 `ApiResponse<T>` 반환
4. **검증 어노테이션**: 입력 검증은 커스텀 검증 어노테이션 활용
5. **예외 처리**: 일관된 예외 처리를 위해 `@ControllerAdvice` 활용

### 보안 고려사항
1. **파일 업로드**: 반드시 `FileSecurityUtil` 사용하여 경로 검증
2. **JWT 토큰**: 민감한 정보는 토큰에 포함하지 않음
3. **레이트 리밋**: IP 기반 요청 제한 준수
4. **세션 관리**: MongoDB TTL 만료 시간을 주기적으로 검증

### WebSocket 이벤트 처리
1. SocketIO 이벤트는 `websocket/` 패키지의 핸들러에서 처리
2. 이벤트 타입은 명확하게 정의하고 문서화
3. 연결 상태 및 오류 처리 로직 필수 포함

### 테스트
- Testcontainers를 사용한 통합 테스트 (MongoDB)
- JUnit 5 기반 단위 테스트
- 테스트 실행에는 Docker 필요

## 문제 해결

일반적인 문제 및 해결 방법은 [README.md의 트러블슈팅](README.md#트러블슈팅) 참조

### 추가 팁
- SocketIO 연결 문제: `socketio.server.port` 설정 확인
- MongoDB 연결 오류: Docker 컨테이너 상태 및 `SPRING_DATA_MONGODB_URI` 환경 변수(`MONGO_URI` 호환) 확인
- Redis 설정은: Redis 서버 가용성 및 `SPRING_DATA_REDIS_HOST`/`SPRING_DATA_REDIS_PORT` 환경 변수 확인
- JWT 토큰 오류: `.env` 파일의 `JWT_SECRET` 설정 확인

## 참고 자료

- [README.md](README.md) - 프로젝트 개요 및 실행 가이드
- [AGENTS.md](AGENTS.md) - AI 에이전트 활용 가이드
- `docs/` 디렉토리 - 설계 문서 및 아키텍처 참고 자료
- `spec/` 디렉토리 - API 명세 및 요구사항
