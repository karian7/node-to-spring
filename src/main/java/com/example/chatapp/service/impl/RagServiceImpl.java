package com.example.chatapp.service.impl;

import com.example.chatapp.model.File;
import com.example.chatapp.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Set;

@Slf4j
@Service
public class RagServiceImpl implements RagService {

    // RAG 시스템에서 처리 가능한 파일 타입들
    private static final Set<String> PROCESSABLE_MIME_TYPES = Set.of(
        "text/plain",
        "text/markdown",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/csv",
        "application/json",
        "text/html",
        "text/xml"
    );

    @Override
    public boolean processFileForRAG(File file, Path filePath) {
        try {
            if (!isProcessableFileType(file.getMimetype())) {
                log.info("파일 타입이 RAG 처리 대상이 아닙니다: {}", file.getMimetype());
                return false;
            }

            log.info("RAG 시스템에 파일 처리 시작: {} ({})", file.getOriginalname(), file.getId());

            // TODO: 실제 RAG 시스템 연동 구현
            // 1. 파일 내용 읽기
            // 2. 텍스트 추출 (PDF, Word 등의 경우)
            // 3. 청크 단위로 분할
            // 4. 임베딩 생성
            // 5. 벡터 데이터베이스에 저장

            // 현재는 로깅만 수행
            log.info("RAG 처리 완료: {}", file.getId());
            return true;

        } catch (Exception e) {
            log.error("RAG 처리 중 에러 발생: {}", file.getId(), e);
            return false;
        }
    }

    @Override
    public boolean removeFileFromRAG(String fileId) {
        try {
            log.info("RAG 시스템에서 파일 제거: {}", fileId);

            // TODO: 실제 RAG 시스템에서 파일 데이터 제거
            // 1. 벡터 데이터베이스에서 해당 파일 관련 임베딩 삭제
            // 2. 메타데이터 정리

            log.info("RAG 데이터 제거 완료: {}", fileId);
            return true;

        } catch (Exception e) {
            log.error("RAG 데이터 제거 중 에러 발생: {}", fileId, e);
            return false;
        }
    }

    @Override
    public boolean isProcessableFileType(String mimeType) {
        return mimeType != null && PROCESSABLE_MIME_TYPES.contains(mimeType.toLowerCase());
    }
}
