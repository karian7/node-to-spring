package com.example.chatapp.websocket.socketio;

/**
 * Socket.IO 이벤트명 상수 클래스
 * Node.js 백엔드와 동일한 이벤트명 보장
 */
public final class SocketIOEvents {
    
    // Client -> Server 이벤트
    public static final String CHAT_MESSAGE = "chatMessage";
    public static final String JOIN_ROOM = "joinRoom";
    public static final String LEAVE_ROOM = "leaveRoom";
    public static final String FETCH_PREVIOUS_MESSAGES = "fetchPreviousMessages";
    public static final String MARK_MESSAGES_AS_READ = "markMessagesAsRead";
    public static final String MESSAGE_REACTION = "messageReaction";
    public static final String TYPING_START = "typing.start";
    public static final String TYPING_STOP = "typing.stop";
    public static final String GET_ONLINE_USERS = "users.online.get";
    
    // Server -> Client 이벤트
    public static final String MESSAGE = "message";
    public static final String MESSAGE_LOAD_START = "messageLoadStart";
    public static final String PREVIOUS_MESSAGES_LOADED = "previousMessagesLoaded";
    public static final String JOIN_ROOM_SUCCESS = "joinRoomSuccess";
    public static final String JOIN_ROOM_ERROR = "joinRoomError";
    public static final String LEAVE_ROOM_SUCCESS = "leaveRoomSuccess";
    public static final String USER_LEFT = "userLeft";
    public static final String PARTICIPANTS_UPDATE = "participantsUpdate";
    public static final String USERS_ONLINE_LIST = "users.online.list";
    
    // AI 스트리밍 이벤트
    public static final String AI_MESSAGE_START = "aiMessageStart";
    public static final String AI_MESSAGE_CHUNK = "aiMessageChunk";
    public static final String AI_MESSAGE_COMPLETE = "aiMessageComplete";
    public static final String AI_MESSAGE_ERROR = "aiMessageError";
    
    // 인증 및 세션 이벤트
    public static final String DUPLICATE_LOGIN = "duplicate_login";
    public static final String SESSION_ENDED = "session_ended";
    public static final String FORCE_LOGIN = "force_login";
    
    // 에러 이벤트
    public static final String ERROR = "error";
    
    // 사용자 상태 이벤트
    public static final String USER_STATUS = "user.status";
    public static final String TYPING = "typing";
    
    private SocketIOEvents() {
        // 유틸리티 클래스이므로 인스턴스 생성 방지
    }
}
