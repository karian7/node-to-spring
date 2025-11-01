package com.ktb.chatapp.service;

import com.ktb.chatapp.websocket.socketio.handler.StreamingSession;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 서비스 인터페이스
 * ChatGPT, Claude 등 다양한 AI 모델 연동 지원
 */
@Service
public interface AiService {
    
    Flux<ChunkData> streamResponse(StreamingSession session);
    
    /**
         * 청크 데이터
         */
    record ChunkData(String currentChunk, boolean codeBlock) {
    }
}
