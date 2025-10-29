---
title: 플랫폼 코어 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/server.js
  - backend/config/keys.js
  - backend/routes/index.js
  - backend/routes/api/index.js
  - backend/middleware/auth.js
  - backend/middleware/upload.js
  - backend/utils/{encryption.js,redisClient.js}
  - backend/services/sessionService.js
---

## 기능 개요
- Express 애플리케이션 부트스트랩, 공통 미들웨어, 라우터 마운트, Socket.IO 초기화를 담당한다.
- 환경 변수(`.env`)를 로드하고 MongoDB, Redis, OpenAI 등 외부 서비스 설정을 관리한다.
- 글로벌 에러 핸들링, 404 응답, 정적 리소스 제공(`/uploads`)을 포함한다.

## 서버 부팅 흐름 (`server.js`)
1. `dotenv` 로드 후 Express 앱 생성.
2. CORS 화이트리스트, `trust proxy`, JSON/URL-encoded 파서 설정.
3. OPTIONS 프리플라이트와 정적 파일(`/uploads`) 처리, 개발 모드 요청 로깅.
4. `/health` 라우트에서 상태/환경 정보 반환.
5. `/api`에 REST 라우트 집약(`routes/index.js`).
6. `http.createServer` + Socket.IO 초기화 → `require('./sockets/chat')(io)` 로 이벤트 바인딩.
7. `initializeSocket(io)`로 Rooms 라우터와 소켓 인스턴스 공유.
8. 404 핸들러, 글로벌 에러 핸들러 등록.
9. MongoDB 연결 성공 시 서버 리스닝(`0.0.0.0:${PORT}`).

## 라우팅 구조
- `/api` 루트: `routes/index.js`에서 auth/users/rooms/files 라우트 마운트, API 문서용 JSON 응답 제공.
- `/api/auth|users|rooms|files`: 세부 REST 엔드포인트 정의 (`routes/api/*`).
- 모든 보호 라우트는 `middleware/auth.js`로 JWT + Redis 세션 검증.
- 업로드 관련 라우트는 `middleware/upload.js`를 통해 파일 필터/사이즈 검사.

## 공용 미들웨어
| 미들웨어 | 역할 |
| --- | --- |
| `cors(corsOptions)` | 화이트리스트 기반 Origin 허용, Credentials·Headers 제어 |
| `express.json()`, `express.urlencoded()` | JSON/폼 파싱 |
| `auth` | `x-auth-token`, `x-session-id` 검증, `SessionService.validateSession` 호출 |
| `upload`/`errorHandler` | 파일 업로드/에러 처리, 허용 MIME·사이즈 검증 |

## 환경 변수 & 설정 (`config/keys.js`)
| 키 | 설명 | 기본값 |
| --- | --- | --- |
| `MONGO_URI` | MongoDB 연결 문자열 | 없음 (필수) |
| `JWT_SECRET` | JWT 서명 키 | 없음 (필수) |
| `ENCRYPTION_KEY` | AES-256 키 (64 hex) | `a` 반복 기본값 |
| `PASSWORD_SALT` | 비밀번호 솔트 (32 hex) | `b` 반복 기본값 |
| `REDIS_HOST`, `REDIS_PORT` | Redis 접속 정보 | 없으면 in-memory mock 사용 |
| `OPENAI_API_KEY` | OpenAI 인증 키 | 없음 (AI 기능 필수) |
| `VECTOR_DB_ENDPOINT` | 벡터 DB 엔드포인트 | 선택 |

## 유틸리티 구성 요소
- **Encryption (`utils/encryption.js`)**: AES-256 암복호화, PBKDF2 해싱, 초기 키/솔트 검증.
- **RedisClient (`utils/redisClient.js`)**: Redis 연결/재시도/Mock 처리, `set/get/setEx/del/expire` 래퍼.
- **SessionService (`services/sessionService.js`)**: Redis 세션 생성/검증/갱신/삭제, 단일 세션 정책 구현, JSON 직렬화 유틸.

## 에러 처리
- 404 핸들러: 요청 경로, 메시지 포함 JSON 응답 반환.
- 글로벌 에러 핸들러: `err.status` 또는 500으로 응답, 개발 모드 시 `stack` 포함.
- 업로드 에러 핸들러: Multer 에러 코드 매핑(413, 400 등) 및 업로드된 임시 파일 삭제.

## 로그 & 관측 가능성
- 개발 모드에서 요청 메서드/URL을 콘솔에 기록.
- Redis 연결/오류, 세션 검증 실패 등은 콘솔 로그로 출력(향후 로거 교체 고려).

## 마이그레이션 메모
- Spring 전환 시 `netty-socketio` + Redisson으로 Socket.IO 서버를 구성하고, Express CORS/미들웨어 동작을 Spring Security/Filter로 옮긴다.
- 환경 변수 관리(`dotenv`)는 Spring Boot의 `application.yml`/`config server`로 대체.
- Redis Mock 사용 전략을 Spring에서도 제공할지(예: 임베디드 Redis, Testcontainers) 결정.
- 글로벌 에러 응답 JSON 포맷을 Spring의 `@ControllerAdvice`로 통일.
- 업로드 디렉토리(`uploads/`)를 로컬이 아닌 S3/Cloud Storage로 이전할지 평가.

## 테스트/검증 노트
- 서버 부트스트랩: `.env` 누락, Mongo 실패, Redis 미구성 등 예외 시나리오 확인.
- `auth` 미들웨어 단위 테스트: 토큰 누락/만료/세션 만료 케이스.
- 업로드 미들웨어: 허용/비허용 MIME, 50MB 초과 파일, 파일명 255자 초과 케이스 검증.
- 통합 테스트: `/health`, `/api` 루트, 404/에러 핸들러 응답 구조 확인.

## Open Questions
- 로깅/모니터링을 structured logger (Winston, Pino)로 대체할 계획이 있는가?
- Redis Mock을 운영 환경에서 허용할지, 혹은 반드시 실 Redis만 사용하도록 강제할지?
- 업로드 디렉토리 권한/보안, 백업 정책을 어떻게 관리할지?
- 환경 변수/비밀 관리는 운영 단계에서 Vault 등 외부 스토리지를 도입할지 초기 설계 단계에서 결정할 것.
