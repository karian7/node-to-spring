package com.example.chatapp.service;

import com.example.chatapp.model.AiType;
import org.springframework.stereotype.Service;


/**
 * AI 서비스 인터페이스
 * ChatGPT, Claude 등 다양한 AI 모델 연동 지원
 */
@Service
public interface AiService {

    /**
     * AI 모델이 사용 가능한지 확인
     * @return 사용 가능 여부
     */
    boolean isAvailable();

    /**
     * AI 응답을 스트리밍으로 생성
     * @param aiType AI 페르소나 타입
     * @param query 사용자 질문
     * @param callbacks 스트리밍 콜백
     */
    void generateStreamingResponse(AiType aiType, String query, StreamingCallbacks callbacks);

    /**
     * 스트리밍 콜백 인터페이스
     */
    interface StreamingCallbacks {
        /**
         * 스트리밍 시작 시 호출
         */
        void onStart();

        /**
         * 청크 수신 시 호출
         * @param chunk 청크 데이터
         */
        void onChunk(ChunkData chunk);

        /**
         * 스트리밍 완료 시 호출
         * @param completion 완료 데이터
         */
        void onComplete(CompletionData completion);

        /**
         * 오류 발생 시 호출
         * @param error 오류
         */
        void onError(Exception error);
    }

    /**
     * 청크 데이터
     */
    class ChunkData {
        private final String currentChunk;
        private final boolean isCodeBlock;

        public ChunkData(String currentChunk, boolean isCodeBlock) {
            this.currentChunk = currentChunk;
            this.isCodeBlock = isCodeBlock;
        }

        public String getCurrentChunk() {
            return currentChunk;
        }

        public boolean isCodeBlock() {
            return isCodeBlock;
        }
    }

    /**
     * 완료 데이터
     */
    class CompletionData {
        private final String content;
        private final Integer completionTokens;
        private final Integer totalTokens;

        public CompletionData(String content, Integer completionTokens, Integer totalTokens) {
            this.content = content;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public String getContent() {
            return content;
        }

        public Integer getCompletionTokens() {
            return completionTokens;
        }

        public Integer getTotalTokens() {
            return totalTokens;
        }
    }
}
