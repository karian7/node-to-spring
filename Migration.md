# Node.js to Java Spring Boot Migration Guide

## Socket.IO Implementation Comparison

### 포트 설정 차이점

#### Node.js 버전
- **동일 포트 사용**: HTTP 서버와 Socket.IO 서버가 같은 포트 사용 가능
- `server.js`에서 `http.createServer(app)`와 `socketIO(server)`로 같은 서버 인스턴스 공유
- 기본 포트: `process.env.PORT || 5000`

```javascript
const server = http.createServer(app);
const io = socketIO(server, { cors: corsOptions });
server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
```

#### Java 버전
- **별도 포트 필요**: Socket.IO 서버가 별도 포트에서 실행
- Spring Boot HTTP 서버와 Socket.IO 서버가 분리됨
- `@Profile("socketio")` 어노테이션으로 조건부 활성화

```java
@Service
@Profile("socketio") // socketio 프로파일이 활성화되어야 실행
public class SocketIOService {
    private final SocketIOServer socketIOServer;
    // 별도 포트에서 Socket.IO 서버 실행
}
```

### 인증 시스템 비교

#### Node.js 버전
- 미들웨어 방식: `io.use()` 사용
- `socket.handshake.auth.token`, `socket.handshake.auth.sessionId` 직접 접근
- 연결 후 `socket.user` 객체에 사용자 정보 저장

```javascript
io.use(async (socket, next) => {
  const token = socket.handshake.auth.token;
  const sessionId = socket.handshake.auth.sessionId;
  // 인증 로직
  socket.user = decoded.user; // 사용자 정보 저장
  next();
});
```

#### Java 버전
- `AuthorizationListener` 인터페이스 구현
- URL 파라미터와 HTTP 헤더에서 인증 정보 추출
- `HandshakeData.getHttpHeaders()`에 사용자 정보 저장

```java
public class SocketIOAuthHandler implements AuthorizationListener {
    @Override
    public boolean isAuthorized(HandshakeData data) {
        String token = extractAuthToken(data);
        String sessionId = extractAuthSessionId(data);
        // 인증 후 헤더에 사용자 정보 저장
        data.getHttpHeaders().set("socket.user.id", user.getId());
        return true;
    }
}
```

### 중복 로그인 처리

#### Node.js 버전
- `Map` 자료구조로 연결된 사용자 관리
- 실시간 중복 로그인 감지 및 이전 세션 종료
- `emit('duplicate_login')` 이벤트로 클라이언트 알림

#### Java 버전
- 현재 구현되지 않음 (향후 구현 필요)

### 마이그레이션 시 주의사항

#### 1. 포트 설정
**❌ 같은 포트 사용 불가능**
- Node.js처럼 Spring Boot HTTP 서버와 Socket.IO를 같은 포트에서 실행할 수 없음
- Socket.IO 서버는 별도 포트(예: 8081)에서 실행 필요

**✅ 해결 방법**
```properties
# application.properties
socketio.server.port=8081
server.port=8080
```

#### 2. 프로파일 활성화
Java 버전에서 Socket.IO를 사용하려면 `socketio` 프로파일 활성화 필요

```bash
java -jar app.jar --spring.profiles.active=socketio
```

#### 3. 클라이언트 연결 URL 변경
**Node.js**: `ws://localhost:5000`
**Java**: `ws://localhost:8081` (별도 포트)

#### 4. CORS 설정
두 버전 모두 CORS 설정이 필요하지만 설정 방법이 다름

**Node.js**:
```javascript
const io = socketIO(server, { cors: corsOptions });
```

**Java**:
```java
// SocketIOConfig.java에서 설정 필요
Configuration config = new Configuration();
config.setAllowedOrigins("*");
```

### 권장사항

1. **개발 환경**: Socket.IO 서버를 별도 포트(8081)에서 실행
2. **프로덕션 환경**: 리버스 프록시(nginx)를 사용하여 단일 포트로 라우팅
3. **클라이언트 코드**: 환경에 따라 Socket.IO 연결 URL 동적 설정

```javascript
const socketUrl = process.env.NODE_ENV === 'production' 
  ? `${window.location.protocol}//${window.location.host}`
  : 'http://localhost:8081';
```

### 미구현 기능 (Java 버전)

- [ ] 중복 로그인 처리
- [ ] 메시지 일괄 로드 및 재시도 로직
- [ ] AI 스트리밍 세션 관리
- [ ] 실시간 읽음 상태 업데이트
- [ ] 파일 업로드 진행률 추적

이러한 기능들은 Node.js 버전을 참고하여 Java로 포팅해야 합니다.
