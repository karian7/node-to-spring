package com.example.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 중복 로그인 상태를 관리하는 책임을 가진 클래스
 */
@Slf4j
@Component
public class PendingDuplicateLoginManager {
    
    private final Map<String, PendingDuplicateLogin> pendingLogins = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "duplicate-login-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    
    private static final int DUPLICATE_LOGIN_TIMEOUT = 10000;
    
    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
    
    /**
     * 새로운 중복 로그인 대기 상태를 등록
     */
    public void registerPending(String userId, String existingSocketId, SocketIOClient newClient,
                                String newSocketId, Runnable onTimeout) {
        // 기존 pending이 있다면 취소
        PendingDuplicateLogin previous = pendingLogins.remove(userId);
        if (previous != null) {
            previous.cancelTimeout();
            log.debug("Cancelled previous pending login for userId: {}", userId);
        }
        
        PendingDuplicateLogin pending = new PendingDuplicateLogin(existingSocketId, newClient, newSocketId);
        
        // 타임아웃 스케줄링
        ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            try {
                onTimeout.run();
            } finally {
                pendingLogins.remove(userId, pending);
            }
        }, DUPLICATE_LOGIN_TIMEOUT, TimeUnit.MILLISECONDS);
        
        pending.setTimeoutTask(timeoutTask);
        pendingLogins.put(userId, pending);
        
        log.debug("Registered pending duplicate login for userId: {}", userId);
    }
    
    /**
     * 중복 로그인 대기 상태를 제거하고 반환
     */
    public PendingDuplicateLogin removePending(String userId) {
        PendingDuplicateLogin pending = pendingLogins.remove(userId);
        if (pending != null) {
            log.debug("Removed pending duplicate login for userId: {}", userId);
        }
        return pending;
    }
    

    /**
     * 특정 socketId가 관련된 중복 로그인 대기 상태가 있는지 확인
     */
    public boolean involvesSocketId(String userId, String socketId) {
        PendingDuplicateLogin pending = pendingLogins.get(userId);
        return pending != null && pending.involves(socketId);
    }
    
    /**
     * Disconnect 시 중복 로그인 처리 - 기존 소켓이 유효하면 복원
     *
     * @param userId 사용자 ID
     * @param disconnectedSocketId 끊어진 소켓 ID
     * @param socketLookup 소켓 조회 함수
     * @param onRestore 기존 소켓 복원 시 실행할 콜백
     * @return 처리 완료 여부
     */
    public boolean handleDisconnectWithRestore(
            String userId,
            String disconnectedSocketId,
            java.util.function.Function<String, SocketIOClient> socketLookup,
            java.util.function.Consumer<String> onRestore) {
        
        PendingDuplicateLogin pending = removePending(userId);
        if (pending == null) {
            return false;
        }
        
        pending.cancelTimeout();
        String existingSocketId = pending.getExistingSocketId();
        
        // 새 연결이 끊어진 경우, 기존 연결이 여전히 유효한지 확인
        if (disconnectedSocketId.equals(pending.getNewSocketId()) && existingSocketId != null) {
            SocketIOClient existingClient = socketLookup.apply(existingSocketId);
            if (existingClient != null && existingClient.isChannelOpen()) {
                // 기존 소켓으로 복원
                onRestore.accept(existingSocketId);
                log.debug("Restored existing socket for userId: {} after new connection disconnected", userId);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 기존 세션 유지 처리 - 새로운 클라이언트를 종료하고 기존 세션 복원
     *
     * @param userId 사용자 ID
     * @param onNewClientDisconnect 새 클라이언트 종료 시 실행할 콜백
     * @param onRestore 기존 소켓 복원 시 실행할 콜백
     * @return 처리 완료 여부 (pending이 없으면 false)
     */
    public boolean handleKeepExistingSession(
            String userId,
            java.util.function.Consumer<SocketIOClient> onNewClientDisconnect,
            java.util.function.Consumer<String> onRestore) {
        
        PendingDuplicateLogin pending = removePending(userId);
        if (pending == null) {
            log.debug("No pending duplicate login found for keep_existing_session - userId: {}", userId);
            return false;
        }
        
        pending.cancelTimeout();
        
        // 새 클라이언트 종료
        SocketIOClient newClient = pending.getNewClient();
        if (newClient != null) {
            onNewClientDisconnect.accept(newClient);
        }
        
        // 기존 소켓으로 복원
        String existingSocketId = pending.getExistingSocketId();
        if (existingSocketId != null) {
            onRestore.accept(existingSocketId);
        }
        
        log.debug("Kept existing session for userId: {}", userId);
        return true;
    }
    
    /**
     * 강제 로그인 처리 - pending 상태 제거 및 타임아웃 취소
     *
     * @param userId 사용자 ID
     * @return 처리 완료 여부 (pending이 없으면 false)
     */
    public boolean handleForceLogin(String userId) {
        PendingDuplicateLogin pending = removePending(userId);
        if (pending != null) {
            pending.cancelTimeout();
            log.debug("Handled force login for userId: {}", userId);
            return true;
        }
        return false;
    }
    
}

