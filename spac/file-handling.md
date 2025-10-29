---
title: 파일 관리 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/routes/api/files.js
  - backend/controllers/fileController.js
  - backend/middleware/upload.js
  - backend/models/File.js
  - backend/services/fileService.js
---

## 기능 개요
- 채팅 메시지 첨부 파일을 업로드/다운로드/미리보기/삭제하는 REST 계층이다.
- 모든 엔드포인트는 `auth` 미들웨어를 거쳐 JWT + Redis 세션 검증 후 수행된다.
- 업로드는 Multer 기반 `upload.single('file')`을 사용하며, 업로드된 파일은 `backend/uploads` 디렉토리에 저장된다.
- 파일 접근 시 메시지와 방 참가자 권한을 검증해 비인가 다운로드를 차단한다.

## 사용자 시나리오
- **파일 업로드**: 사용자가 채팅 방에서 파일 첨부 시 `/api/files/upload`로 보내고, 성공 시 파일 메타데이터를 받은 뒤 `chatMessage` 이벤트에서 첨부 ID를 참조한다. (메시지와의 연동 로직은 [`messages-history.md`](messages-history.md) 참고)
- **파일 다운로드**: `/api/files/download/:filename`으로 파일을 내려받고, Content-Disposition 헤더를 통해 안전한 파일명을 제공한다.
- **파일 미리보기**: 이미지/동영상 등 미리보기가 가능한 MIME은 `/api/files/view/:filename`으로 인라인 응답한다.
- **파일 삭제**: 업로더만 `/api/files/:id`로 삭제 가능하며, 파일 시스템과 DB 모두에서 제거된다.

## HTTP 인터페이스
| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| POST | `/api/files/upload` | `auth`, `multipart/form-data` | 첨부 파일 업로드 (`file` 필드 사용) |
| GET | `/api/files/download/:filename` | `auth` | 파일 다운로드 (권한 검증 포함) |
| GET | `/api/files/view/:filename` | `auth` | 파일 미리보기 (지원 MIME 한정) |
| DELETE | `/api/files/:id` | `auth` | 업로드한 사용자가 파일 삭제 |

## 요청/응답 스키마

### POST /api/files/upload
- **Headers**: `x-auth-token`, `x-session-id`, `Content-Type: multipart/form-data`
- **Form Data**: `file` (50MB 이하, 허용 MIME은 upload 미들웨어 참고)
- **Response 200**
```json
{
  "success": true,
  "message": "파일 업로드 성공",
  "file": {
    "_id": "6561...",
    "filename": "1700000000000_abcd1234.pdf",
    "originalname": "보고서.pdf",
    "mimetype": "application/pdf",
    "size": 34567,
    "uploadDate": "2025-10-30T07:00:00.000Z"
  }
}
```
- **Error 400**: `{ "success": false, "message": "파일이 선택되지 않았습니다." }`
- **Error 500**: `{ "success": false, "message": "파일 업로드 중 오류가 발생했습니다.", "error": "..." }`

### GET /api/files/download/:filename
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**: 바이너리 스트림, 헤더
  - `Content-Type: <file.mimetype>`
  - `Content-Disposition: attachment; filename="..."; filename*=UTF-8''...`
  - `Cache-Control: private, no-cache, no-store, must-revalidate`
- **Error 응답** (handleFileError)
```json
{
  "success": false,
  "message": "파일을 찾을 수 없습니다." | "파일에 접근할 권한이 없습니다." | "잘못된 파일명입니다." | ...
}
```
  - 상태코드: 400/401/403/404/500 매핑

### GET /api/files/view/:filename
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**: 인라인 스트림, 헤더
  - `Content-Type: <file.mimetype>`
  - `Content-Disposition: inline; filename=...`
  - `Cache-Control: public, max-age=31536000, immutable`
- **Error 415**: `{ "success": false, "message": "미리보기를 지원하지 않는 파일 형식입니다." }`
- 기타 오류는 download와 동일한 스키마

### DELETE /api/files/:id
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "message": "파일이 삭제되었습니다."
}
```
- **Error 404**: `{ "success": false, "message": "파일을 찾을 수 없습니다." }`
- **Error 403**: `{ "success": false, "message": "파일을 삭제할 권한이 없습니다." }`
- **Error 500**: `{ "success": false, "message": "파일 삭제 중 오류가 발생했습니다.", "error": "..." }`

## 업로드 미들웨어 규칙 (`backend/middleware/upload.js`)
- 허용 MIME/확장자: 이미지(JPEG/PNG/GIF/WebP), 비디오(MP4/WebM/MOV), 오디오(MP3/WAV/OGG), 문서(PDF/DOC/DOCX 등).
- 파일 크기 제한: MIME별 상한(이미지 10MB, 비디오 50MB, 오디오 20MB, 문서 20MB) + Multer 글로벌 50MB.
- 파일명 검증: UTF-8 255바이트 이하, 확장자와 MIME 일치.
- 저장 파일명: `<timestamp>_<randomHex>.<ext>`.
- 업로드 실패 시 파일 삭제 및 에러 메시지 반환 (Multer 에러는 400/413 등으로 매핑).

## 권한/검증 흐름
1. 업로드 후 파일 문서 생성 → 업로더 `user` 필드 저장.
2. 다운로드/미리보기 시 `getFileFromRequest`에서 다음 확인:
   - filename / token / sessionId 유효성.
   - 파일 존재 여부 및 파일 경로 안전성 (`isPathSafe`).
   - 파일 관련 메시지와 방 참가 여부 검증.
3. 삭제 시 업로더만 허용 (`file.user === req.user.id`).
4. 모든 경로에서 파일 시스템 접근 전 안전 경로 확인.

## 데이터 모델
- **File**
  - 필드: `filename`, `originalname`(정규화), `mimetype`, `size`, `user`, `path`, `uploadDate`.
  - 인덱스: `(filename, user)` unique.
  - 메서드: `getContentDisposition`, `getFileUrl`, `isPreviewable` 등.
  - pre-remove 훅: 파일 삭제 (fs.unlink).
- **Message**
  - 파일 메시지 전송 시 `file` 참조 및 `metadata.fileType/fileSize/originalName` 저장.
- **Room**
  - 다운로드/뷰 시 방 참가자 권한 검증에 사용.

## 비즈니스 규칙
- 업로드 성공 후 반드시 `fs.rename`으로 안전한 파일명으로 이동.
- 업로드 실패 시 임시 파일 제거하여 디스크 누수 방지.
- 다운로드/미리보기 응답은 캐시 헤더(S3 대신 로컬 파일)로 설정 (view는 장기 캐시 허용).
- 파일 삭제 시 DB 삭제 실패가 발생해도 응답은 500에 error 메시지 포함.
- 첨부 파일은 메시지와 연결되어야 하며, 메시지가 없으면 접근 거부.
- `processFileForRAG` 호출은 현재 컨트롤러에서 사용되지 않으나, 향후 RAG 처리를 위해 파일 경로를 넘길 수 있도록 설계.

## 예외/에러 매핑
| 상황 | 상태 코드 | 메시지 |
| --- | --- | --- |
| filename 누락 | 400 | `잘못된 파일명입니다.` |
| 인증 헤더 없음 | 401 | `인증이 필요합니다.` |
| 경로 탐지 실패 | 400/403 | `잘못된 파일 경로입니다.` |
| DB에 파일 없음 | 404 | `파일을 찾을 수 없습니다.` |
| 메시지/방 없음 | 404/403 | `파일 메시지를 찾을 수 없습니다.` / `파일에 접근할 권한이 없습니다.` |
| 미리보기 비지원 | 415 | `미리보기를 지원하지 않는 파일 형식입니다.` |
| Multer LIMIT_FILE_SIZE | 413 | `파일 크기는 50MB를 초과할 수 없습니다.` |
| Multer 기타 | 400 | `한 번에 하나의 파일만 업로드할 수 있습니다.` 등 |
| 서버 오류 | 500 | `파일 처리 중 오류가 발생했습니다.` |

## 외부 의존성
- **fs / path / crypto**: 파일 시스템 작업, 안전한 파일명, 경로 검증.
- **multer**: 업로드 처리 및 에러 코드.
- **pdf-parse** (fileService): PDF 추출 (현재 호출되지 않음).
- **SessionService + auth 미들웨어**: 파일 접근 전 세션 검증.

## 마이그레이션 메모
- Spring 전환 시 멀티파트 처리를 `Spring MultipartResolver`와 S3/로컬 저장 전략 중 결정해야 한다.
- 권한 검증 로직(파일→메시지→Room 참가자)을 재사용 가능한 서비스로 추출하고, WebFlux/REST 양쪽에서 호출할 수 있도록 설계.
- 캐시 헤더(다운로드, 미리보기) 정책을 클라우드 스토리지에 맞게 조정.
- 에러 매핑을 표준 응답 포맷(JSON API 등)으로 통일.
- RAG 처리(`processFileForRAG`)가 미완성 → Spring 구현 시 비동기 큐/백그라운드 워커로 분리 고려.

## 테스트/검증 노트
- 업로드 성공/실패 케이스: 허용/비허용 MIME, 50MB 이상 파일, 파일명 255자 초과.
- 다운로드 권한 테스트: 다른 방 사용자가 접근 시 403 기대.
- 미리보기: 미지원 MIME 요청 시 415 응답 확인.
- 삭제: 업로더, 비업로더, 존재하지 않는 파일 ID 케이스.
- 파일 시스템 누락 테스트: 파일 삭제 후 다운로드 접근 시 404 응답.

## Open Questions
- 장기적으로 로컬 저장 대신 S3 등 외부 스토리지를 사용할 계획이 있는가?
- 파일 메시지 삭제와 Message soft delete 간 동기화 전략이 필요한가?
- `processFileForRAG`와 같은 후처리 흐름을 언제 호출할지, 실패 시 재시도 전략은 무엇인지?
- 파일 버전 관리 또는 중복 제거 전략이 필요한가?
