package com.example.chatapp.service;

import com.example.chatapp.model.AiType;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * AI 서비스 인터페이스
 * ChatGPT, Claude 등 다양한 AI 모델 연동 지원
 */
@Service
public interface AiService {

    /**
     * 메시지에 대한 AI 응답 생성
     * @param prompt 사용자 메시지
     * @param roomId 채팅방 ID (컨텍스트용)
     * @param userId 사용자 ID
     * @return AI 응답 메시지
     */
    String generateResponse(String prompt, String roomId, String userId);

    /**
     * 파일 내용 기반 AI 응답 생성 (RAG 연동)
     * @param prompt 질문
     * @param fileId 참조할 파일 ID
     * @param userId 사용자 ID
     * @return AI 응답
     */
    String generateResponseWithFileContext(String prompt, String fileId, String userId);

    /**
     * 대화 요약 생성
     * @param roomId 채팅방 ID
     * @param messageCount 요약할 메시지 수
     * @return 대화 요약
     */
    String summarizeConversation(String roomId, int messageCount);

    /**
     * AI 모델이 사용 가능한지 확인
     * @return 사용 가능 여부
     */
    boolean isAvailable();

    void generateStreamingResponse(AiType aiTypeEnum, String query, Consumer<String> c, Runnable r);
}
