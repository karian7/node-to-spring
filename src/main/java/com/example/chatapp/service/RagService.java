package com.example.chatapp.service;

import com.example.chatapp.model.File;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * RAG (Retrieval-Augmented Generation) 시스템 연동 서비스
 * AI 벡터 데이터베이스에 파일 내용을 처리하여 저장
 */
@Service
public interface RagService {

    /**
     * 파일을 RAG 시스템에서 처리 가능한 형태로 변환하고 벡터 DB에 저장
     * @param file 파일 메타데이터
     * @param filePath 실제 파일 경로
     * @return 처리 성공 여부
     */
    boolean processFileForRAG(File file, Path filePath);

    /**
     * RAG 시스템에서 파일 데이터 제거
     * @param fileId 파일 ID
     * @return 제거 성공 여부
     */
    boolean removeFileFromRAG(String fileId);

    /**
     * 파일 타입이 RAG 처리 가능한지 확인
     * @param mimeType 파일 MIME 타입
     * @return 처리 가능 여부
     */
    boolean isProcessableFileType(String mimeType);
}
