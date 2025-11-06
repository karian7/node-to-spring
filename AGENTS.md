# Repository Guidelines

## 빠른 시작 리소스
- 로컬 실행, 필수 환경 변수, Docker 의존성, 기본 테스트 방법은 [README.md](./README.md)에서 항상 최신 상태로 유지합니다. 중복 정보를 추가하지 말고 README 섹션을 참고 링크로 안내하세요.
- 새로 합류한 기여자는 `README.md`의 `## 프로젝트 구조`, `## 환경 변수 설정`, `## 애플리케이션 실행`, `## 테스트`를 차례로 확인한 뒤 이 문서를 읽으면 흐름이 자연스럽습니다.

## 코드베이스 탐색
- 주요 소스는 `src/main/java/com/ktb/chatapp` 아래에 있으며, 패키지는 기능 단위(`controller`, `service`, `repository`, `websocket`, `security`, `config`, `dto`, `model`, `validation`, `exception`)로 나뉩니다.
- 테스트는 동일한 패키지 구조를 `src/test/java/com/ktb/chatapp`에서 따릅니다. 새 테스트를 추가할 때는 해당 클래스와 동일한 패키지 경로에 `*Test` 접미사를 사용하세요.
- 참고 문서 및 설계 기록은 `docs/`와 `spac/`에 있습니다. 실시간 처리, 인증, 파일 업로드 등 세부 설계는 `spac` 디렉터리 문서를 먼저 확인하세요.

## 개발 원칙
- Java 21과 Spring Boot 3.5를 기준으로 하며, Google Java Style을 따릅니다 (4-space indent, UpperCamelCase 타입명, lowerCamelCase 필드명).
- 빈 주입은 생성자 주입을 기본으로 하고 Lombok을 일관되게 사용합니다. 필요 시 Lombok 어노테이션 적용 범위를 최소화하면서도 가독성을 유지하세요.
- REST 엔드포인트는 `controller`, 비즈니스 로직은 `service`, 데이터 접근은 `repository`에 배치합니다. DTO는 요청/응답 모델을 명확히 분리하고, 엔티티 변환은 서비스 계층에서 처리합니다.
- 코드 자체로 의도를 전달하도록 설계하고, 주석은 복잡한 비즈니스 규칙이나 외부 의존성을 설명해야 할 때만 첨부합니다.

## 테스트 전략
- JUnit 5와 Testcontainers를 사용합니다. 통합 테스트가 컨테이너를 기동하므로 Docker가 준비되어 있어야 합니다.
- 서비스 레벨 테스트에서는 외부 시스템(예: OpenAI, 외부 API)을 명시적으로 스텁하거나 모의 객체로 대체해 결정적 결과를 검증하세요.
- MockMvc 혹은 WebTestClient를 사용해 HTTP 계약을 검증하며, 예상된 에러 응답(HTTP 상태·바디 구조)을 함께 확인합니다.
- 테스트 네이밍은 `methodName_condition_expectedResult` 패턴을 권장하며, 실패 시 원인을 바로 파악할 수 있도록 assertion 메시지를 구체적으로 작성합니다.

## 커밋 & PR 규칙
- 커밋 메시지는 `type(scope): subject` 형태의 [Conventional Commits](https://www.conventionalcommits.org/)를 따릅니다. subject는 50자 이하, 현재형(명령형)으로 작성합니다.
- 한 커밋에는 하나의 논리적 변경만 담습니다. 사소한 수정이라도 주석, 포맷 변경 등은 별도 커밋으로 분리하세요.
- PR 설명에는 변경 요약과 함께 테스트 결과, 새로운 환경 변수 또는 설정 변경 사항, 참고해야 할 문서 링크를 포함합니다.

## 보안 & 구성
- 비밀 값은 `.env` 또는 환경 변수로만 관리합니다. 환경 변수 전체 목록과 기본값은 [README.md#환경-변수-설정](./README.md#환경-변수-설정)을 참고하세요.
- `src/main/resources/application.properties`에는 안전한 기본값만 두고, 민감 정보는 `${ENV_VAR}` 형태로 주입합니다.
- 릴리스 전에 OpenAI, MongoDB 등 외부 서비스 연결 정보가 확인되었는지 점검하고, 필요 시 `docs/`에 운영 체크리스트를 갱신합니다.
