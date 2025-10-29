---
title: AI 통합 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/services/aiService.js
  - backend/sockets/chat.js
  - backend/models/Message.js
  - backend/services/sessionService.js
---

## 기능 개요
- OpenAI Chat Completions API를 스트리밍 방식으로 호출해 채팅방 내 AI 응답을 생성한다.
- 클라이언트는 메시지 본문에 `@wayneAI` 또는 `@consultingAI`를 멘션하면 AI 응답이 트리거된다.
- `aiService.generateResponse`가 스트림 이벤트(`onStart`, `onChunk`, `onComplete`, `onError`)를 발행하고, Socket.IO가 `aiMessageStart/Chunk/Complete/Error` 이벤트로 브로드캐스트한다.
- 최종 AI 응답은 MongoDB `Message` 문서로 저장되어 대화 이력에 남는다.

## 페르소나 정의
| 코드 | 이름 | 역할 | 특성 | 톤 |
| --- | --- | --- | --- | --- |
| `wayneAI` | Wayne AI | 친절한 어시스턴트 | 통찰력 있는 답변, 명확한 설명 | 전문적이면서 친근 |
| `consultingAI` | Consulting AI | 비즈니스 컨설턴트 | 전략/시장/조직 조언 | 전문적이고 분석적 |

- 시스템 프롬프트는 페르소나 이름/역할/특성/톤을 포함한다.
- 다른 페르소나 요청 시 `Unknown AI persona` 오류를 발생시킨다.

## 트리거 조건
1. 사용자가 텍스트 메시지를 전송할 때 `extractAIMentions`가 `@wayneAI` 또는 `@consultingAI`를 정규식으로 찾는다.
2. 중복 멘션은 Set으로 제거하여 페르소나별로 한 번씩만 호출한다.
3. Mentions를 제거한 질문 텍스트(`query`)를 OpenAI에 전달한다.

## OpenAI 요청 사양
- **Endpoint**: `POST https://api.openai.com/v1/chat/completions`
- **Headers**: `Authorization: Bearer <OPENAI_API_KEY>`, `Content-Type: application/json`
- **Payload**
```json
{
  "model": "gpt-4",
  "messages": [
    { "role": "system", "content": "...페르소나 안내..." },
    { "role": "user", "content": "질문 내용" }
  ],
  "temperature": 0.7,
  "stream": true
}
```
- 응답은 Server-Sent Events 형식으로 도착하며, `data: {"choices":[{"delta":{"content":"..."}}]}`를 파싱한다.

## Socket 이벤트 흐름
| 순서 | 이벤트 | 방향 | 페이로드 |
| --- | --- | --- | --- |
| 1 | `aiMessageStart` | server→room | `{ messageId, aiType, timestamp }` |
| 2 | `aiMessageChunk` | server→room | `{ messageId, currentChunk, fullContent, isCodeBlock, aiType, timestamp, isComplete:false }` |
| 3 | `aiMessageComplete` | server→room | `{ messageId, _id, content, aiType, timestamp, reactions:{} , isComplete:true, query }` |
| 4 | (오류 시) `aiMessageError` | server→room | `{ messageId, error, aiType }` |

- `messageId`는 `${aiType}-${Date.now()}` 형식의 임시 ID로 스트리밍 컨텍스트를 구분한다.
- `streamingSessions` Map에 `{ room, aiType, content, lastUpdate }`를 저장해 스트림 상태를 추적한다.

## 메시지 저장 형식 (최종)
> 저장된 AI 메시지가 사용자 이력에 어떻게 노출되는지는 [`messages-history.md`](messages-history.md)를 참고하세요.
```json
{
  "_id": "6562...",
  "room": "6560...",
  "type": "ai",
  "aiType": "wayneAI",
  "content": "AI가 생성한 답변",
  "timestamp": "2025-10-30T07:15:00.000Z",
  "metadata": {
    "query": "사용자 질문",
    "generationTime": 1234,
    "completionTokens": 200,
    "totalTokens": 350
  },
  "reactions": {}
}
```

## 오류/예외 흐름
- OpenAI 스트림 파싱 오류 → `callbacks.onError` → `aiMessageError` 이벤트.
- API 호출 실패 → `AI 응답 생성 중 오류가 발생했습니다.` 예외 발생.
- 스트리밍 중 네트워크 에러 → `Stream error` 로그 후 `aiMessageError`.
- 알 수 없는 페르소나 → `Unknown AI persona` throw, 클라이언트에는 일반 `error` 이벤트로 전달.

## 보안/검증
- AI 메시지를 생성하기 전 `SessionService.validateSession`과 채팅방 권한 확인이 이미 선행된다.
- `aiService`는 OpenAI 키를 환경 변수에서 읽어 Axios 인스턴스를 구성한다.
- 멘션 텍스트를 제거한 순수 질문만 OpenAI에 전달해 개인정보 노출을 최소화한다.

## 마이그레이션 메모
- Spring 전환 시 WebClient(WebFlux) 또는 OpenAI SDK를 사용해 SSE 스트림을 처리하고, `netty-socketio` 기반 Socket.IO 서버로 동일 이벤트를 전파해야 한다.
- 스트림 처리 시 백프레셔, 연결 중단 대비 재시도 전략을 설계해야 한다 (현재는 단순 에러 이벤트).
- OpenAI 모델/옵션을 설정 파일로 외부화하고, 페르소나 목록을 구성 파일/DB로 관리할지 결정 필요.
- 토큰 사용량(`completionTokens`, `totalTokens`)은 Node 코드에서 아직 실제 응답을 받지 않으므로, Spring 구현 시 OpenAI 응답의 usage 필드를 활용.

## 테스트/검증 노트
- 멘션 포함 메시지 전송 후 `aiMessageStart→Chunk→Complete` 순서 확인.
- 동시에 여러 AI 멘션이 있을 때 각각 독립 스트림으로 처리되는지 검증.
- OpenAI API 키 누락/유효성 실패 시 오류 이벤트가 발생하는지 확인.
- AI 응답 저장 후 메시지 재로드(`fetchPreviousMessages`) 시 AI 메시지가 포함되는지 검증.

## Open Questions
- OpenAI 모델/버전을 사용자 설정으로 바꿀 계획이 있는가?
- AI 응답의 민감한 정보 필터링이나 안전 가드가 필요한가?
- 스트림 타임아웃/취소 기능을 제공할 것인가? (현재는 클라이언트 취소 로직 없음)
- 멘션 외 UI 트리거(버튼/슬래시 명령)를 추가할 계획이 있는가?
