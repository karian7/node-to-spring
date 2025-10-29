---
title: 사용자 계정 스펙
status: Draft
last_reviewed: 2025-10-30
owner: TBD
node_sources:
  - backend/routes/api/users.js
  - backend/controllers/userController.js
  - backend/middleware/auth.js
  - backend/middleware/upload.js
  - backend/models/User.js
---

## 기능 개요
- 사용자 등록, 프로필 조회/수정, 프로필 이미지 관리, 계정 삭제까지 제공하는 REST 계층이다.
- `POST /api/users/register`는 새 사용자를 생성하지만 인증 토큰이나 세션을 발급하지 않는다. 로그인은 `/api/auth/login`을 별도로 호출해야 한다.
- 인증이 필요한 엔드포인트들은 `auth` 미들웨어를 통해 JWT + Redis 세션(`x-auth-token`, `x-session-id`)을 검증한 뒤 `req.user.id`를 주입받는다.
- 프로필 이미지는 로컬 파일 시스템(`backend/uploads`)에 저장하며, 새로운 이미지를 업로드하면 이전 파일을 제거한다.

## 사용자 시나리오
- **회원가입**: 필수 필드(name, email, password)를 검증하고, 중복 이메일이 없으면 사용자 문서를 생성한다. 응답에는 기본 프로필 정보만 포함되며 토큰은 없다.
- **프로필 조회**: 인증된 사용자가 자신의 이름/이메일/프로필 이미지를 확인한다. 존재하지 않으면 404.
- **프로필 수정**: 이름만 수정 가능하며, 공백이거나 2자 미만이면 400을 반환한다.
- **프로필 이미지 업로드**: `multipart/form-data` 요청에서 `profileImage` 필드를 5MB 이하 이미지로 제출한다. 기존 이미지가 있으면 삭제 후 새 경로(`/uploads/<파일>`)를 저장한다.
- **프로필 이미지 삭제**: 저장된 경로를 제거하고 파일을 삭제한 뒤 성공 메시지를 반환한다.
- **회원 탈퇴**: 사용자 문서와 연결된 프로필 이미지를 삭제한다.
- **상태 확인**: `/api/users/status`로 API 동작 여부를 확인한다.

## HTTP 인터페이스
| 타입 | 메서드 | 경로 | 인증 | 요약                                                           |
| --- | --- | --- | --- |--------------------------------------------------------------|
| HTTP | POST | `/api/users/register` | 불필요 | 신규 사용자 생성, JWT 미발급 **[⚠️ 사용하지 않음: `/api/auth/register` 사용]** |
| HTTP | GET | `/api/users/profile` | `auth` | 현재 사용자 프로필 조회                                                |
| HTTP | PUT | `/api/users/profile` | `auth` | 이름 업데이트                                                      |
| HTTP | POST | `/api/users/profile-image` | `auth` + `multipart/form-data` | 프로필 이미지 업로드 (`profileImage` 필드)                              |
| HTTP | DELETE | `/api/users/profile-image` | `auth` | 프로필 이미지 삭제                                                   |
| HTTP | DELETE | `/api/users/account` | `auth` | 계정 삭제                                                        |
| HTTP | GET | `/api/users/status` | 불필요 | 상태 체크 JSON                                                   |

## 요청/응답 스키마

#### POST /api/users/register
- **Request (JSON)**
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
  "user": {
    "id": "6560...",
    "name": "홍길동",
    "email": "user@example.com",
    "profileImage": ""
  }
}
```

#### GET /api/users/profile
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "user": {
    "id": "6560...",
    "name": "홍길동",
    "email": "user@example.com",
    "profileImage": "/uploads/1700000000000_abcdef.png"
  }
}
```

#### PUT /api/users/profile
- **Headers**: `x-auth-token`, `x-session-id`
- **Request (JSON)**
```json
{
  "name": "새 이름"
}
```
- **Response 200**
```json
{
  "success": true,
  "message": "프로필이 업데이트되었습니다.",
  "user": {
    "id": "6560...",
    "name": "새 이름",
    "email": "user@example.com",
    "profileImage": ""
  }
}
```

#### POST /api/users/profile-image
- **Headers**: `x-auth-token`, `x-session-id`, `Content-Type: multipart/form-data`
- **Form Data**: `profileImage` (이미지 파일, 5MB 이하)
- **Response 200**
```json
{
  "success": true,
  "message": "프로필 이미지가 업데이트되었습니다.",
  "imageUrl": "/uploads/1700000000000_abcd1234.png"
}
```

#### DELETE /api/users/profile-image
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "message": "프로필 이미지가 삭제되었습니다."
}
```

#### DELETE /api/users/account
- **Headers**: `x-auth-token`, `x-session-id`
- **Response 200**
```json
{
  "success": true,
  "message": "회원 탈퇴가 완료되었습니다."
}
```

#### GET /api/users/status
- **Response 200**
```json
{
  "success": true,
  "message": "User API is running",
  "timestamp": "2025-10-30T05:30:00.000Z"
}
```

## 예외 응답 스키마
- **회원가입 (400 - 유효성 실패)**
```json
{
  "success": false,
  "errors": [
    { "field": "name", "message": "이름을 입력해주세요." },
    { "field": "password", "message": "비밀번호는 6자 이상이어야 합니다." }
  ]
}
```
- **회원가입 (409 - 이메일 중복)**
```json
{
  "success": false,
  "message": "이미 가입된 이메일입니다."
}
```
- **프로필 조회/수정/이미지/탈퇴 (404 - 사용자 없음)**
```json
{
  "success": false,
  "message": "사용자를 찾을 수 없습니다."
}
```
- **프로필 수정 (400 - 이름 누락)**
```json
{
  "success": false,
  "message": "이름을 입력해주세요."
}
```
- **이미지 업로드 (400 - 파일 누락/형식 불일치/5MB 초과)**
```json
{
  "success": false,
  "message": "이미지가 제공되지 않았습니다." | "이미지 파일만 업로드할 수 있습니다." | "파일 크기는 5MB를 초과할 수 없습니다."
}
```
- **이미지 업로드 (413 - Multer LIMIT_FILE_SIZE)**
```json
{
  "success": false,
  "message": "파일 크기는 5MB를 초과할 수 없습니다." | "파일 크기는 50MB를 초과할 수 없습니다."
}
```
- **이미지 업로드 (400 - 기타 Multer/파일 필터 오류)**
```json
{
  "success": false,
  "message": "잘못된 파일 필드입니다." | "지원하지 않는 이미지 형식입니다." | "파일 업로드 중 오류가 발생했습니다."
}
```
- **공통 서버 오류 (500)**
```json
{
  "success": false,
  "message": "회원가입 처리 중 오류가 발생했습니다." | "프로필 조회 중 오류가 발생했습니다." | "이미지 업로드 중 오류가 발생했습니다." | "회원 탈퇴 처리 중 오류가 발생했습니다."
}
```

## 데이터 모델
- **User (MongoDB)**
  - 필드: `name`, `email`(고유 & 소문자 저장, 정규식 검증), `encryptedEmail`(AES-256-CBC), `password`(bcrypt 해시, select=false), `profileImage`(String, 기본 빈 문자열), `createdAt`, `lastActive`.
  - 미들웨어: 저장 전 비밀번호 재해시, 이메일 변경 시 암호화.
  - 메서드: `matchPassword`, `updateProfile`, `deleteAccount`, `changePassword`, `decryptEmail` 등.
- **파일 저장소**
  - 경로: `backend/uploads`. 존재하지 않을 경우 생성 및 권한 0755.
  - 파일명: `<timestamp>_<random-hex>.<ext>` 형식으로 저장.
  - 허용 확장자: JPEG/PNG/GIF/WebP 등 이미지, 추가적으로 동영상/오디오/문서 확장자를 지원하지만 사용자 프로필 이미지는 컨트롤러에서 이미지 MIME만 허용.

## 비즈니스 규칙
- 이름은 최소 2자 이상, 공백 제거 후 저장.
- 이메일은 유일해야 하며, 등록 시 대소문자 구분 없이 저장 및 암호화(encryptedEmail) 유지.
- 회원가입 시 기본 `profileImage`는 빈 문자열, 이후 업로드 시 `/uploads/`로 시작하는 상대 경로로 저장.
- 프로필 이미지는 5MB 초과 시 거부하며, 허용되는 MIME 타입은 `image/*` 범위로 제한된다.
- 새 이미지 업로드 시 기존 파일을 삭제하여 저장소 누수를 방지한다 (삭제 실패는 로그만 남기고 계속 진행).
- 모든 보호된 라우트는 인증 미들웨어를 거치므로 요청자는 반드시 유효한 세션을 유지해야 한다.

## 에러/예외
| 엔드포인트 | 조건 | 상태 코드 | 메시지/코드 |
| --- | --- | --- | --- |
| Register | 유효성 실패 | 400 | `errors` 배열 |
| Register | 이메일 중복 | 409 | `이미 가입된 이메일입니다.` |
| Profile 조회/수정/이미지/탈퇴 | 사용자 없음 | 404 | `사용자를 찾을 수 없습니다.` |
| Profile 수정 | 이름 누락 | 400 | `이름을 입력해주세요.` |
| Profile 이미지 | 파일 누락/형식 오류 | 400 | `이미지가 제공되지 않았습니다.` 등 |
| Profile 이미지 | 용량 초과 | 413 | `파일 크기는 5MB를 초과할 수 없습니다.` |
| Account 삭제 | 성공 | 200 | `회원 탈퇴가 완료되었습니다.` |
| 공통 | 서버 내부 오류 | 500 | 컨텍스트별 메시지 |

## 외부 의존성
- **환경 변수**: `ENCRYPTION_KEY`, `PASSWORD_SALT`, `MONGO_URI` (User 모델 저장/암호화 의존).
- **파일 시스템**: `backend/uploads` 디렉토리에 쓰기 권한 필요. 운영 환경에선 외부 스토리지(S3 등)로 전환 고려.
- **Multer**: 파일 업로드 처리. 제한값(`limits`)과 `fileFilter` 로직이 프로필 이미지 정책에 영향을 준다.

## 마이그레이션 메모
- Spring 구현 시 프로필 이미지를 어디에 저장할지 결정하고, 기존 이미지 삭제/교체 전략을 동일하게 유지하거나 오브젝트 스토리지로 재설계해야 한다.
- **`/api/users/register` 구현 제외**: 현재 프로젝트에서는 `/api/auth/register`(토큰/세션 발급)만 사용하므로, Spring에서는 토큰 미발급 회원가입 엔드포인트를 별도 구현하지 않는다. 향후 필요 시 추가 검토.
- Multer의 예외 코드(예: `LIMIT_FILE_SIZE`)에 대응하는 HTTP 상태/메시지를 Spring에서도 재현하거나 공통 에러 포맷을 도입한다.
- Node 구현은 이미지 MIME과 확장자를 이중 검증한다. Spring에서도 파일 필터와 MIME 검증을 분리/이중화할지 결정 필요.

## 테스트/검증 노트
- 단위 테스트: 이메일/이름/비밀번호 유효성 실패 케이스 및 중복 이메일 시나리오 검증.
- 통합 테스트: 인증 미들웨어 포함하여 프로필 CRUD 시나리오 수행 (MockMvc + 임베디드 Mongo/Redis).
- 파일 업로드 테스트: 5MB 초과, 비이미지 파일, 파일명 255자 초과 등 실패 케이스와 성공 케이스 검증.
- 계정 삭제 후 참조 데이터 정리 TODO (Spring 전환 시 연관 데이터 삭제 여부 검토 필요).

## Open Questions
- 프로필 이미지가 로컬 파일 시스템에 저장되는데, 멀티 인스턴스 환경에서 공유 스토리지로 전환할 계획이 있는지 확인 필요.
- Multer `limits.fileSize`는 50MB로 설정되어 있으나 컨트롤러는 5MB를 허용한다. Spring 이식 시 어느 기준을 표준으로 삼을지 결정해야 한다.
- `User.deleteAccount`가 연관 메시지/파일 등 다른 도메인 데이터를 정리하지 않는다. Spring 전환 시 추가 삭제 로직 또는 soft delete 전략이 필요한지 검토해야 한다.
