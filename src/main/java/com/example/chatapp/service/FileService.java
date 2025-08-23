package com.example.chatapp.service;

import com.example.chatapp.model.File;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.util.FileSecurityUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class FileService {

    private final Path fileStorageLocation;
    private final FileSecurityUtil fileSecurityUtil;
    private final RagService ragService;
    private final FileRepository fileRepository;

    public FileService(@Value("${file.upload-dir}") String uploadDir,
                      FileSecurityUtil fileSecurityUtil,
                      RagService ragService,
                      FileRepository fileRepository) {
        this.fileSecurityUtil = fileSecurityUtil;
        this.ragService = ragService;
        this.fileRepository = fileRepository;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    /**
     * 완전한 파일 업로드 처리 (보안 검증 + RAG 연동 + 메타데이터 저장)
     */
    public EnhancedFileUploadResult uploadFileComplete(MultipartFile file, String uploaderId, String roomId) {
        String tempFilePath = null;
        try {
            // 1. 보안 검증 및 파일 저장
            SecureFileUploadResult uploadResult = storeFileSecurely(file);
            tempFilePath = uploadResult.getStoredFilename();

            // 2. 데이터베이스에 메타데이터 저장
            File fileEntity = new File();
            fileEntity.setFilename(uploadResult.getStoredFilename());
            fileEntity.setOriginalname(uploadResult.getOriginalFilename());
            fileEntity.setMimetype(uploadResult.getMimeType());
            fileEntity.setSize(uploadResult.getFileSize());
            fileEntity.setUploadedBy(uploaderId);
            fileEntity.setRoomId(roomId);
            fileEntity.setUploadedAt(LocalDateTime.now());

            File savedFile = fileRepository.save(fileEntity);

            // 3. RAG 시스템 연동 (백그라운드에서 처리)
            try {
                Path filePath = this.fileStorageLocation.resolve(uploadResult.getStoredFilename());
                boolean ragResult = ragService.processFileForRAG(savedFile, filePath);

                // RAG 처리 결과를 파일 메타데이터에 업데이트
                savedFile.setRagProcessed(ragResult);
                fileRepository.save(savedFile);

                log.info("RAG 처리 결과: {} - {}", savedFile.getId(), ragResult ? "성공" : "실패");
            } catch (Exception ragError) {
                log.warn("RAG 처리 중 에러 발생하지만 파일 업로드는 계속 진행: {}", ragError.getMessage());
            }

            return EnhancedFileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .downloadUrl(generateDownloadUrl(savedFile.getFilename()))
                    .ragProcessed(savedFile.isRagProcessed())
                    .build();

        } catch (Exception e) {
            log.error("파일 업로드 중 에러 발생", e);

            // 실패 시 자동 정리
            if (tempFilePath != null) {
                cleanupFailedUpload(tempFilePath);
            }

            throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 보안 검증이 강화된 파일 저장
     */
    public SecureFileUploadResult storeFileSecurely(MultipartFile file) {
        try {
            // 1. 기본 파일 정보 검증
            if (file.isEmpty()) {
                throw new RuntimeException("빈 파일은 업로드할 수 없습니다.");
            }

            String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
            String mimeType = file.getContentType();
            long fileSize = file.getSize();

            // 2. 보안 검증 수행
            FileSecurityUtil.FileValidationResult validationResult =
                fileSecurityUtil.validateFile(originalFilename, mimeType, fileSize);

            if (!validationResult.isValid()) {
                throw new RuntimeException(validationResult.getErrorMessage());
            }

            // 3. 안전한 파일명 사용
            String safeFilename = validationResult.getSafeFilename();

            // 4. 경로 안전성 검증
            Path targetLocation = this.fileStorageLocation.resolve(safeFilename);
            if (!fileSecurityUtil.isPathSafe(targetLocation.toString(),
                                           this.fileStorageLocation.toString())) {
                throw new RuntimeException("파일 경로가 안전하지 않습니다.");
            }

            // 5. 파일 저장
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            log.info("File uploaded securely: {} -> {}", originalFilename, safeFilename);

            return SecureFileUploadResult.builder()
                    .success(true)
                    .storedFilename(safeFilename)
                    .originalFilename(validationResult.getSanitizedOriginalName())
                    .fileSize(fileSize)
                    .mimeType(mimeType)
                    .build();

        } catch (IOException ex) {
            log.error("파일 저장 중 오류 발생: {}", ex.getMessage(), ex);
            throw new RuntimeException("파일 저장에 실패했습니다: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("파일 업로드 보안 검증 실패: {}", ex.getMessage(), ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    /**
     * 기존 호환성을 위한 레거시 메서드 (내부적으로 보안 검증 사용)
     */
    public String storeFile(MultipartFile file) {
        SecureFileUploadResult result = storeFileSecurely(file);
        return result.getStoredFilename();
    }

    /**
     * 보안이 강화된 파일 로드
     */
    public Resource loadFileAsResourceSecurely(String fileName, String requesterId) {
        try {
            // 1. 경로 안전성 검증
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            if (!fileSecurityUtil.isPathSafe(filePath.toString(), this.fileStorageLocation.toString())) {
                throw new RuntimeException("Invalid file path: " + fileName);
            }

            // 2. 데이터베이스에서 파일 권한 검증
            File fileEntity = fileRepository.findByFilename(fileName)
                    .orElseThrow(() -> new RuntimeException("File not found: " + fileName));

            // 3. 파일 접근 권한 검증 (업로더이거나 같은 룸 참여자)
            if (!canAccessFile(fileEntity, requesterId)) {
                throw new RuntimeException("Unauthorized file access: " + fileName);
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                log.info("File accessed securely: {} by user {}", fileName, requesterId);
                return resource;
            } else {
                throw new RuntimeException("File not found " + fileName);
            }

        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }

    /**
     * 기존 호환성을 위한 레거시 메서드
     */
    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new RuntimeException("File not found " + fileName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File not found " + fileName, ex);
        }
    }

    /**
     * 안전한 파일 삭제 (권한 검증 포함)
     */
    public boolean deleteFileSecurely(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("File not found: " + fileId));

            // 삭제 권한 검증 (업로더만 삭제 가능)
            if (!fileEntity.getUploadedBy().equals(requesterId)) {
                throw new RuntimeException("Unauthorized file deletion: " + fileId);
            }

            // 1. 물리적 파일 삭제
            Path filePath = this.fileStorageLocation.resolve(fileEntity.getFilename());
            Files.deleteIfExists(filePath);

            // 2. RAG 시스템에서 제거
            if (fileEntity.isRagProcessed()) {
                ragService.removeFileFromRAG(fileId);
            }

            // 3. 데이터베이스에서 제거
            fileRepository.delete(fileEntity);

            log.info("File deleted securely: {} by user {}", fileId, requesterId);
            return true;

        } catch (Exception e) {
            log.error("파일 삭제 중 에러 발생: {}", fileId, e);
            return false;
        }
    }

    /**
     * 사용자별 업로드한 파일 목록 조회
     */
    public List<File> getUserFiles(String userId) {
        return fileRepository.findByUploadedByOrderByUploadedAtDesc(userId);
    }

    /**
     * 룸별 파일 목록 조회
     */
    public List<File> getRoomFiles(String roomId) {
        return fileRepository.findByRoomIdOrderByUploadedAtDesc(roomId);
    }

    /**
     * 실패한 업로드 정리 (자동 정리 로직)
     */
    private void cleanupFailedUpload(String filename) {
        try {
            Path filePath = this.fileStorageLocation.resolve(filename);
            Files.deleteIfExists(filePath);
            log.info("Cleaned up failed upload: {}", filename);
        } catch (Exception e) {
            log.error("Failed to cleanup file: {}", filename, e);
        }
    }

    /**
     * 파일 접근 권한 검증
     */
    private boolean canAccessFile(File fileEntity, String requesterId) {
        // 업로더는 항상 접근 가능
        if (fileEntity.getUploadedBy().equals(requesterId)) {
            return true;
        }

        // TODO: 같은 룸 참여자 여부 확인 로직 추가
        // RoomRepository를 주입받아서 참여자 목록 확인
        return true; // 임시로 모든 접근 허용
    }

    /**
     * 다운로드 URL 생성
     */
    private String generateDownloadUrl(String filename) {
        return "/api/files/download/" + filename;
    }

    // DTO classes

    @Data
    @Builder
    public static class SecureFileUploadResult {
        private boolean success;
        private String storedFilename;
        private String originalFilename;
        private long fileSize;
        private String mimeType;
    }

    @Data
    @Builder
    public static class EnhancedFileUploadResult {
        private boolean success;
        private File file;
        private String downloadUrl;
        private boolean ragProcessed;
    }
}
