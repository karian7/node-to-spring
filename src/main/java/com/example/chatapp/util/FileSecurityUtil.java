package com.example.chatapp.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Component
public class FileSecurityUtil {

    // 허용된 파일 확장자 목록
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
        ".jpg", ".jpeg", ".png", ".gif", ".pdf", ".doc", ".docx",
        ".txt", ".xlsx", ".xls", ".ppt", ".pptx", ".zip", ".rar"
    );

    // 허용된 MIME 타입 목록
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "application/pdf",
        "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain", "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/zip", "application/x-rar-compressed"
    );

    // 최대 파일 크기 (10MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // 파일명에 사용할 수 없는 문자들
    private static final Pattern INVALID_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 경로 안전성 검증 (Path Traversal 공격 방지)
     */
    public boolean isPathSafe(String filePath, String allowedDirectory) {
        try {
            Path resolvedPath = Paths.get(filePath).toRealPath();
            Path allowedPath = Paths.get(allowedDirectory).toRealPath();
            return resolvedPath.startsWith(allowedPath);
        } catch (IOException e) {
            log.warn("Path validation failed for: {}", filePath, e);
            return false;
        }
    }

    /**
     * 안전한 파일명 생성
     */
    public String generateSafeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            originalFilename = "file";
        }

        // 파일 확장자 추출
        String extension = "";
        int lastDotIndex = originalFilename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < originalFilename.length() - 1) {
            extension = originalFilename.substring(lastDotIndex).toLowerCase();
        }

        // 확장자 검증
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            extension = ".bin"; // 허용되지 않은 확장자는 .bin으로 변경
        }

        // 타임스탬프 + 랜덤 바이트로 안전한 파일명 생성
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomBytes = new byte[8];
        secureRandom.nextBytes(randomBytes);
        String randomHex = bytesToHex(randomBytes);

        return timestamp + "_" + randomHex + extension;
    }

    /**
     * 파일 확장자 검증
     */
    public boolean isAllowedExtension(String filename) {
        if (filename == null) return false;

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex <= 0) return false;

        String extension = filename.substring(lastDotIndex).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    /**
     * MIME 타입 검증
     */
    public boolean isAllowedMimeType(String mimeType) {
        return mimeType != null && ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    /**
     * 파일 크기 검증
     */
    public boolean isValidFileSize(long fileSize) {
        return fileSize > 0 && fileSize <= MAX_FILE_SIZE;
    }

    /**
     * 파일명 유효성 검증 (특수문자 제거)
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) return "file";

        // 위험한 문자들 제거
        String sanitized = filename.replaceAll("[<>:\"/\\\\|?*]", "_");

        // 연속된 점들 제거 (../ 공격 방지)
        sanitized = sanitized.replaceAll("\\.{2,}", "_");

        // 파일명이 점으로 시작하는 것 방지
        if (sanitized.startsWith(".")) {
            sanitized = "_" + sanitized.substring(1);
        }

        // 파일명 길이 제한 (최대 100자)
        if (sanitized.length() > 100) {
            String extension = "";
            int lastDotIndex = sanitized.lastIndexOf('.');
            if (lastDotIndex > 0) {
                extension = sanitized.substring(lastDotIndex);
                sanitized = sanitized.substring(0, Math.min(100 - extension.length(), lastDotIndex)) + extension;
            } else {
                sanitized = sanitized.substring(0, 100);
            }
        }

        return sanitized;
    }

    /**
     * 종합적인 파일 보안 검증
     */
    public FileValidationResult validateFile(String originalFilename, String mimeType, long fileSize) {
        FileValidationResult result = new FileValidationResult();

        // 파일명 검증
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            result.setValid(false);
            result.setErrorMessage("파일명이 유효하지 않습니다.");
            return result;
        }

        // 확장자 검증
        if (!isAllowedExtension(originalFilename)) {
            result.setValid(false);
            result.setErrorMessage("허용되지 않은 파일 형식입니다. 허용된 형식: " + String.join(", ", ALLOWED_EXTENSIONS));
            return result;
        }

        // MIME 타입 검증
        if (!isAllowedMimeType(mimeType)) {
            result.setValid(false);
            result.setErrorMessage("허용되지 않은 파일 타입입니다.");
            return result;
        }

        // 파일 크기 검증
        if (!isValidFileSize(fileSize)) {
            result.setValid(false);
            result.setErrorMessage("파일 크기가 제한을 초과했습니다. 최대 크기: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            return result;
        }

        result.setValid(true);
        result.setSafeFilename(generateSafeFilename(originalFilename));
        result.setSanitizedOriginalName(sanitizeFilename(originalFilename));

        return result;
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 파일 검증 결과를 담는 클래스
     */
    @Getter
    @Setter
    public static class FileValidationResult {
        private boolean valid;
        private String errorMessage;
        private String safeFilename;
        private String sanitizedOriginalName;
    }
}
