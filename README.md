# Chat App Backend (Spring Boot)

## 개요
Spring Boot 3.5와 Java 21을 사용해 구축한 실시간 채팅 백엔드입니다. MongoDB를 통한 영속화와 Redis 기반 세션/레이트리밋, JWT 인증, OpenAI 연동을 제공하며 Socket.IO 호환 실시간 메시징을 지원합니다.

## 주요 기술 스택
- Java 21, Spring Boot 3.5 (Web, Validation, Security, OAuth2 Resource Server)
- MongoDB 6, Redis 7 (로컬 또는 Docker Compose)
- Netty Socket.IO 서버 (`com.corundumstudio:netty-socketio`)
- Spring Security + JWT, 커스텀 레이트 리미터
- Spring AI(OpenAI) 기반 대화형 응답 생성
- Testcontainers, JUnit 5, Reactor Test를 이용한 검증

## 프로젝트 구조
```text
src/main/java/com/ktb/chatapp
├── controller   # REST 엔드포인트
├── service      # 도메인 비즈니스 로직
├── repository   # MongoDB 접근 계층
├── websocket    # Socket.IO 서버/핸들러
├── security     # 인증/인가 설정
├── config       # 공통 설정(Async, Redis, Retry 등)
├── dto | model  # 요청/응답 DTO 및 엔티티
└── validation   # 커스텀 검증 로직

docs/, spac/     # 설계 및 아키텍처 참고 자료
```

## 사전 준비물
- Java 21 (JDK) 설치
- Docker & Docker Compose (권장) 또는 자체 MongoDB/Redis 인스턴스
- make (선택 사항, 편의 명령 제공)

## 환경 변수 설정
애플리케이션은 `.env` 혹은 호스트 환경 변수에서 설정을 읽습니다. 필수 항목을 채운 뒤 서버를 시작하세요.

| 변수 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- |
| `ENCRYPTION_KEY` | ✅ | 없음 | AES-256 암복호화를 위한 64자리 HEX 키 |
| `PASSWORD_SALT` | ✅ | 없음 | 비밀번호 해시에 사용하는 솔트 값 |
| `JWT_SECRET` | ✅ | 없음 | HMAC-SHA256 JWT 서명 비밀키 |
| `MONGO_URI` | ❌ | `mongodb://localhost:27017/bootcamp-chat` | MongoDB 연결 문자열 |
| `REDIS_HOST` | ❌ | `localhost` | Redis 호스트 |
| `REDIS_PORT` | ❌ | `6379` | Redis 포트 |
| `PORT` | ❌ | `5001` | HTTP API 포트 (`server.port`) |
| `WS_PORT` | ❌ | `5002` | Socket.IO 서버 포트 |
| `OPENAI_API_KEY` | ❌ | `your_openai_api_key_here` | OpenAI 호출용 API Key |
| `OPENAI_MODEL` | ❌ | `gpt-4.1-mini` | OpenAI 챗 모델 ID |
| `OPENAI_TEMPERATURE` | ❌ | `0.7` | OpenAI 응답 온도 |

예시:
```bash
cp .env .env.backup 2>/dev/null || true
cat > .env <<'ENV'
ENCRYPTION_KEY=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
PASSWORD_SALT=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
JWT_SECRET=your-secure-jwt-secret
OPENAI_API_KEY=sk-...
ENV
```

## 종속 서비스 실행
```bash
docker-compose up -d mongo redis
```
MongoDB와 Redis가 이미 실행 중이라면 이 단계를 건너뛸 수 있습니다.

## 애플리케이션 실행
가장 간편한 방법은 Maven Wrapper와 Makefile을 사용하는 것입니다.

```bash
make dev      # dev 프로파일로 실행 (Testcontainers 지원)
make build    # 패키지 및 테스트 수행
make test     # 단위/통합 테스트 실행
```

직접 Maven 명령을 사용할 수도 있습니다.
```bash
./mvnw clean install          # 전체 빌드 및 테스트
./mvnw spring-boot:run        # 애플리케이션 실행
java -jar target/chat-app-0.0.1-SNAPSHOT.jar
```
기본 포트는 HTTP `5001`, Socket.IO `5002`입니다.

## 테스트
```bash
./mvnw test
```
테스트는 JUnit 5와 Testcontainers를 사용하며, Docker가 필요할 수 있습니다. 로컬에서 서비스가 실행 중이면 Testcontainers는 자동으로 재사용합니다.

## 트러블슈팅
- `.env`의 필수 키가 누락되면 애플리케이션이 부팅 중 예외를 발생시킵니다.
- MongoDB/Redis 연결 오류 시 `docker-compose ps`로 컨테이너 상태를 확인하거나 `application.properties`의 기본값을 검토하세요.
- OpenAI 통합을 사용하지 않을 경우 `OPENAI_API_KEY`를 제거하면 관련 기능은 비활성화됩니다.
