package com.example.chatapp.service.impl;

import com.example.chatapp.model.AiType;
import com.example.chatapp.service.AiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * AI 서비스 구현체
 * 향후 ChatGPT, Claude 등 실제 AI 모델 연동 시 이 클래스를 확장
 */
@Slf4j
@Service
public class AiServiceImpl implements AiService {

    @Override
    public String generateResponse(String prompt, String roomId, String userId) {
        try {
            // TODO: 실제 AI API 연동 구현
            // 현재는 간단한 응답만 제공

            log.info("AI 응답 요청: 룸 {} - 사용자 {} - 프롬프트: {}", roomId, userId, prompt);

            if (prompt.toLowerCase().contains("안녕") || prompt.toLowerCase().contains("hello")) {
                return "안녕하세요! 무엇을 도와드릴까요?";
            }

            if (prompt.toLowerCase().contains("시간")) {
                return "현재 시간은 " + java.time.LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " 입니다.";
            }

            if (prompt.toLowerCase().contains("도움") || prompt.toLowerCase().contains("help")) {
                return "저는 채팅방의 AI 어시스턴트입니다. " +
                       "@ai 또는 !ask를 사용하여 질문해주세요. " +
                       "파일 관련 질문이나 대화 요약도 가능합니다.";
            }

            // 기본 응답
            return "죄송하지만 현재는 간단한 응답만 가능합니다. " +
                   "더 자세한 AI 기능은 향후 업데이트에서 제공될 예정입니다.";

        } catch (Exception e) {
            log.error("AI 응답 생성 중 오류", e);
            return "AI 응답 생성 중 오류가 발생했습니다.";
        }
    }

    @Override
    public String generateResponseWithFileContext(String prompt, String fileId, String userId) {
        try {
            log.info("파일 기반 AI 응답 요청: 파일 {} - 사용자 {} - 프롬프트: {}", fileId, userId, prompt);

            // TODO: RAG 시스템과 연동하여 파일 내용 기반 응답 생성
            return "파일 내용을 분석하여 답변을 생성하는 기능은 현재 개발 중입니다. " +
                   "향후 업데이트에서 제공될 예정입니다.";

        } catch (Exception e) {
            log.error("파일 기반 AI 응답 생성 중 오류", e);
            return "파일 분석 중 오류가 발생했습니다.";
        }
    }

    @Override
    public String summarizeConversation(String roomId, int messageCount) {
        try {
            log.info("대화 요약 요청: 룸 {} - 메시지 수: {}", roomId, messageCount);

            // TODO: 실제 대화 내용을 분석하여 요약 생성
            return String.format("최근 %d개 메시지의 대화 요약:\n" +
                               "채팅방에서 활발한 대화가 이루어지고 있습니다. " +
                               "상세한 요약 기능은 향후 AI 모델 연동 시 제공될 예정입니다.", messageCount);

        } catch (Exception e) {
            log.error("대화 요약 생성 중 오류", e);
            return "대화 요약 생성 중 오류가 발생했습니다.";
        }
    }

    @Override
    public boolean isAvailable() {
        // 현재는 기본 응답만 가능한 상태
        return true;
    }

    @Override
    public void generateStreamingResponse(AiType aiTypeEnum, String query, Consumer<String> c, Runnable r) {

    }

}
