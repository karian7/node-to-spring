package com.example.chatapp.repository;

import com.example.chatapp.model.File;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends MongoRepository<File, String> {
    Optional<File> findByFilename(String filename);

    // 사용자별 업로드한 파일 목록 (최신순)
    List<File> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    // 룸별 파일 목록 (최신순)
    List<File> findByRoomIdOrderByUploadedAtDesc(String roomId);

    // RAG 처리된 파일 목록
    List<File> findByRagProcessedTrue();

    // RAG 처리 대기 중인 파일 목록
    List<File> findByRagProcessedFalse();
}
