# /spac OVERVIEW

## 디렉토리 목적
- `backend/` Node.js 구현을 기능 단위로 해부해 Spring 마이그레이션 기준 스펙을 축적하는 저장소
- 각 스펙 문서에서 API, 데이터 모델, 비즈니스 로직, 에러 처리, 외부 연동까지 정리하여 양 스택 간 파리티를 추적
- 문서 상태를 `Migration.md`와 연동해 어떤 범위가 완료/보류인지 한눈에 파악

## 문서 작성 원칙
- 파일명은 `kebab-case.md`; 한 기능 당 하나의 문서로 유지 (예: `auth-session.md`)
- 필수 섹션: `기능 개요`, `사용자 시나리오`, `HTTP/Socket 인터페이스`, `요청/응답 스키마`, `예외 응답 스키마`, `데이터 모델`, `비즈니스 규칙`, `에러/예외`, `외부 의존성`, `마이그레이션 메모`, `테스트/검증 노트`
- Node 구현과 Spring 타깃의 차이를 명확히 기록하고, 의도적인 편차는 근거와 함께 명시
- 스펙 초안 작성 시 관련 경로(컨트롤러, 서비스, 모델, 미들웨어, 소켓)를 모두 열거하고, 확인한 커밋/버전을 첨부
- 문서 갱신 시 `Migration.md`의 해당 항목을 업데이트하고, 추가 확인이 필요한 TODO는 문서 최하단에 `Open Questions` 섹션으로 남김

## 기능 분류 및 스펙 매핑
| 기능 그룹 | 설명 | 주요 코드 경로 | 예정 문서 | 현재 상태 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| 인증 · 세션 | 회원가입/로그인, 토큰 검증, Redis 세션 동시 로그인 제어 | `backend/controllers/authController.js`<br>`backend/routes/api/auth.js`<br>`backend/middleware/auth.js`<br>`backend/services/sessionService.js`<br>`backend/utils/redisClient.js` | [`auth-session.md`](auth-session.md) | API 전반 구현; 세션 강제 로그아웃 로직 존재 | 1 |
| 사용자 계정 | 프로필 조회/수정, 이미지 업로드·삭제, 회원 탈퇴 | `backend/controllers/userController.js`<br>`backend/routes/api/users.js`<br>`backend/middleware/upload.js`<br>`backend/models/User.js` | [`user-account.md`](user-account.md) | 기능 구현 완료, 업로드 에러 처리 포함 | 1 |
| 채팅방 관리 | 방 CRUD, 참가/입장, 헬스 체크, Room 소켓 브로드캐스트 | `backend/routes/api/rooms.js`<br>`backend/models/Room.js`<br>`backend/sockets/chat.js` (room 이벤트) | [`rooms-lifecycle.md`](rooms-lifecycle.md) | REST + Socket 모두 활용, 이벤트 동기화 중요 | 1 |
| 메시지 기록 | 메시지 조회 REST, 메시지 모델, 읽음 처리, 첨부파일 연동 | `backend/controllers/messageController.js`<br>`backend/routes/api/message.js`<br>`backend/models/Message.js` | [`messages-history.md`](messages-history.md) | REST `loadMessages` 미구현, 소켓 측에서만 처리 | 2 |
| 실시간 채팅 | Socket.IO 인증, presence 트래킹, 메시지 스트리밍, 중복 로그인 제어 | `backend/sockets/chat.js`<br>`backend/services/sessionService.js` | [`realtime-transport.md`](realtime-transport.md) | 대규모 로직, 재시도 & 스트리밍 처리 포함 | 1 |
| 파일 관리 | 첨부 업로드/다운로드/삭제, 파일 메타데이터 저장 | `backend/controllers/fileController.js`<br>`backend/routes/api/files.js`<br>`backend/models/File.js`<br>`uploads/` | [`file-handling.md`](file-handling.md) | 업로드/다운로드 로직 존재, 권한 체크 필요 | 2 |
| AI 어시스턴트 | OpenAI 호출, 스트리밍 응답, 페르소나 구성 | `backend/services/aiService.js`<br>`backend/sockets/chat.js` (AI 이벤트) | [`ai-integration.md`](ai-integration.md) | 외부 API 연동, 스트림 파싱 로직 구현 | 3 |
| 플랫폼 공통 | 서버 부트스트랩, 설정, 보안 유틸, 공용 미들웨어 | `backend/server.js`<br>`backend/config/`<br>`backend/middleware/`<br>`backend/utils/encryption.js` | [`platform-core.md`](platform-core.md) | 구성 전반 파악 필요, 다른 스펙의 기반 | 3 |

> 우선순위 1: 즉시 스펙화 필요 (마이그레이션 블로킹). 2: 핵심 기능 종속성 높음. 3: 지원/부가 기능.

## 단계별 작성 로드맵
1. **인증 · 세션**: 세션 서비스와 소켓 중복 로그인 제어가 Spring 보안 구성에 직결 → 가장 먼저 세부 스펙 초안 작성.
2. **사용자 계정 & 채팅방 관리**: Spring DTO/서비스 구조 설계 선행을 위해 필요한 필드/검증/에러 목록 확정.
3. **실시간 채팅**: Socket 이벤트 흐름·에러 시나리오 문서화 후 REST 메시지/파일과 교차 참조.
4. **메시지 기록 · 파일 관리**: REST 미구현 부분은 Node 기준 기대 동작을 정리하고, Spring 구현 TODO를 명시.
5. **AI & 플랫폼 공통**: 외부 의존성, 설정 항목, 운영 로깅 등 보강.

## 유지보수 체크리스트
- 새 스펙 파일 생성 시 `OVERVIEW.md` 테이블의 상태/우선순위와 `Migration.md` 해당 항목을 동기화
- 기능 추가·변경 발생 시 Node와 Spring 간 차이, 테스트 커버리지, 데이터 마이그레이션 고려사항을 문서화
- 스펙 검토 후 검증 로그(테스트 명령, Postman 캡처 등)를 각 문서 `테스트/검증 노트`에 링크
- 문서 리뷰 완료 시 최종 확인자와 날짜를 스펙 상단 메타데이터에 기록해 추적성 확보
- **매 단계 유지할 베스트 프랙티스**: 신규 스펙 초안 완료 시 해당 문서 링크를 테이블에 즉시 추가하고, 작성 과정에서 드러난 통찰을 `최근 인사이트` 섹션에 축적한다.

## 최근 인사이트
- `SessionService`에 선언되지 않은 `refreshSession`, `getSocketId` 호출이 남아 있어 실제 동작을 재확인해야 함. 다른 기능 정리 시에도 유사한 누락 여부를 점검하도록 가이드.
- `/api/auth/verify-token` 주석과 실제 HTTP 메서드가 불일치하므로, 라우트 파일과 문서화된 계약을 항상 교차 검증할 것.
- Redis 미구성 시 MockRedis로 대체되어 세션이 프로세스 범위에만 유지됨. 세션 의존 기능 스펙을 작성할 때 환경별 제약을 명시하고 Spring 전환 시 운영 Redis 의무화를 고려.
- 동일한 "register" 기능이 `/api/auth/register`(세션 발급)와 `/api/users/register`(세션 미발급)로 이중 구현되어 있으니, 문서화 시 사용할 소스를 명확히 지정하고 중복 정의 여부를 점검할 것.
- 인증 계층의 에러 응답은 대부분 `{ success: false, message, code? }` 패턴을 따르므로, 이후 스펙에서도 동일 스키마 재사용 여부를 확인해 공통 에러 규약을 정의할 것.
- `/api/users/register`는 토큰을 발급하지 않으니 인증 시나리오를 문서화할 때 `/api/auth/register`와 혼동하지 않도록 구분하고, 마이그레이션 시에도 동일 정책을 유지할 것.
- 프로필 이미지 업로드는 컨트롤러에서 5MB, Multer에서 50MB 제한을 중첩 적용 중이므로, 다음 스펙에서 파일 크기 정책을 추적해 일관된 기준을 마련할 것.
- 방 목록 API는 60회/분 rate limit과 `TOO_MANY_REQUESTS` 에러 포맷을 사용하므로, 이후 스펙에서도 공통 쓰로틀링 정책을 정의할지 검토할 것.
- `roomCreated`/`roomUpdate` 이벤트는 정해진 Socket.IO 룸(`room-list`, 방 ID)에 송출되므로, Spring 전환 시 `netty-socketio` + Redisson 기반 Socket.IO 환경에서 채널 구조를 문서화하고 구독 전략을 조기 결정할 것.
- 메시지 REST 엔드포인트가 500으로 응답하므로, Spring 전환 시 Socket 페이로드를 표준화해 REST와 WS 양쪽에서 재사용할 스키마를 정의해야 한다.
- 메시지 읽음/리액션 이벤트는 단순 문자열 오류 응답만 제공하니, 공통 에러 코드를 도입할지 추후 스펙에서 결정해야 한다.
- 실시간 계층은 사용자→소켓 1:1 매핑을 가정하므로 멀티 인스턴스 시 공유 스토리지(예: Redis pub/sub) 필요 여부를 조기에 검토할 것.
- 세션 검증 실패 시 Socket 오류 메시지가 문자열 위주로 전달되므로, Spring 전환 시 WebSocket 에러 프레임 포맷을 표준화할 것.
- 파일 다운로드 권한은 메시지/방 참여 여부로 확인하므로, Spring 구현에서도 파일→메시지→Room 체인을 재사용 가능한 서비스로 추출할 것.
- Multer와 컨트롤러의 파일 크기 제한이 다르므로, 일관된 정책을 설정하고 클라이언트와 공유할 것.
- `processFileForRAG`가 아직 호출되지 않아 RAG 후처리 시점/실패 처리 전략이 미정; 추후 파일 업로드 후 비동기 작업을 정의해야 한다.
- AI 멘션은 텍스트 기반이므로 UI 트리거(버튼/슬래시)를 추가할 경우 동일 페르소나 스펙을 재사용하도록 API 계약을 확장해야 한다.
- OpenAI 스트림은 단일 연결 기준으로 처리되므로 멀티 AI 호출 시 리소스 관리(동시 호출 제한, 타임아웃)를 정의할 것.
