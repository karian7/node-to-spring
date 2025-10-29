package com.example.chatapp.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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
        "jpg", "jpeg", "png", "gif", "pdf", "doc", "docx",
        "txt", "xlsx", "xls", "ppt", "pptx", "zip", "rar", "webp"
    );

    // 허용된 MIME 타입 목록
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
        "image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf",
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

    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * 파일 유효성 검증
     */
    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("파일이 비어있습니다.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new RuntimeException("파일명이 올바르지 않습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("파일 크기가 10MB를 초과할 수 없습니다.");
        }

        // 파일 확장자 검증
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new RuntimeException("허용되지 않은 파일 형식입니다. 허용 형식: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // MIME 타입 검증
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new RuntimeException("허용되지 않은 파일 타입입니다.");
        }
    }

    /**
     * 경로 안전성 검증 (Path Traversal 공격 방지)
     */
    public static void validatePath(Path filePath, Path allowedDirectory) {
        try {
            Path normalizedPath = filePath.normalize();
            Path normalizedAllowedDir = allowedDirectory.normalize();

            if (!normalizedPath.startsWith(normalizedAllowedDir)) {
                throw new RuntimeException("허용되지 않은 파일 경로입니다.");
            }
        } catch (Exception e) {
            log.error("경로 검증 실패: {}", e.getMessage());
            throw new RuntimeException("파일 경로가 안전하지 않습니다.");
        }
    }

    /**
     * 파일 확장자 추출
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "";
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDot + 1);
    }

    /**
     * 안전한 파일명 생성
     */
    public static String generateSafeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return generateRandomFileName("file");
        }

        // 파일 확장자 분리
        String extension = getFileExtension(originalFilename);
        String nameWithoutExtension = originalFilename;
        if (!extension.isEmpty()) {
            nameWithoutExtension = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        }

        // 특수문자 제거 및 안전한 문자로 변경
        String safeName = INVALID_FILENAME_PATTERN.matcher(nameWithoutExtension).replaceAll("_");

        // 길이 제한 (50자)
        if (safeName.length() > 50) {
            safeName = safeName.substring(0, 50);
        }

        // 타임스탬프와 랜덤 값 추가로 고유성 보장
        long timestamp = Instant.now().toEpochMilli();
        int random = secureRandom.nextInt(1000);

        return String.format("%s_%d_%03d.%s", safeName, timestamp, random, extension);
    }

    /**
     * 랜덤 파일명 생성
     */
    private static String generateRandomFileName(String prefix) {
        long timestamp = Instant.now().toEpochMilli();
        int random = secureRandom.nextInt(10000);
        return String.format("%s_%d_%04d", prefix, timestamp, random);
    }

    /**
     * 파일명 정리 (특수문자 제거)
     */
    public static String sanitizeFileName(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "file";
        }

        return INVALID_FILENAME_PATTERN.matcher(filename.trim()).replaceAll("_");
    }

    /**
     * Path Traversal 공격 감지
     */
    public static boolean containsPathTraversal(String filename) {
        if (filename == null) {
            return false;
        }

        String normalized = filename.toLowerCase();
        return normalized.contains("..") ||
               normalized.contains("/") ||
               normalized.contains("\\") ||
               normalized.contains("%2e%2e") ||
               normalized.contains("0x2e0x2e");
    }
}
