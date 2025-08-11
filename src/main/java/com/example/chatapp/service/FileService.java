package com.example.chatapp.service;

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

@Slf4j
@Service
public class FileService {

    private final Path fileStorageLocation;
    private final FileSecurityUtil fileSecurityUtil;

    public FileService(@Value("${file.upload-dir}") String uploadDir,
                      FileSecurityUtil fileSecurityUtil) {
        this.fileSecurityUtil = fileSecurityUtil;
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
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
     * 보안 검증이 강화된 파일 로드
     */
    public Resource loadFileAsResourceSecurely(String fileName, String userId) {
        try {
            // 1. 파일명 보안 검증
            if (fileName == null || fileName.contains("..")) {
                throw new RuntimeException("유효하지 않은 파일명입니다: " + fileName);
            }

            // 2. 경로 안전성 검증
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            if (!fileSecurityUtil.isPathSafe(filePath.toString(),
                                           this.fileStorageLocation.toString())) {
                throw new RuntimeException("파일 경로가 안전하지 않습니다.");
            }

            // 3. 파일 존재 확인
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                log.debug("File accessed by user {}: {}", userId, fileName);
                return resource;
            } else {
                throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
            }
        } catch (MalformedURLException ex) {
            log.error("파일 로드 중 오류 발생: {}", ex.getMessage(), ex);
            throw new RuntimeException("파일 로드에 실패했습니다: " + fileName, ex);
        }
    }

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
     * 파일 삭제 (보안 검증 포함)
     */
    public boolean deleteFileSecurely(String fileName, String userId) {
        try {
            // 1. 파일명 보안 검증
            if (fileName == null || fileName.contains("..")) {
                log.warn("Invalid file deletion attempt by user {}: {}", userId, fileName);
                return false;
            }

            // 2. 경로 안전성 검증
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            if (!fileSecurityUtil.isPathSafe(filePath.toString(),
                                           this.fileStorageLocation.toString())) {
                log.warn("Unsafe file deletion attempt by user {}: {}", userId, fileName);
                return false;
            }

            // 3. 파일 삭제
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("File deleted by user {}: {}", userId, fileName);
            }
            return deleted;

        } catch (IOException ex) {
            log.error("파일 삭제 중 오류 발생: {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 보안 파일 업로드 결과를 담는 클래스
     */
    @Data
    @Builder
    public static class SecureFileUploadResult {
        private boolean success;
        private String storedFilename;
        private String originalFilename;
        private long fileSize;
        private String mimeType;
        private String errorMessage;
    }
}
