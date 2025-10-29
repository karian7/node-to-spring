package com.example.chatapp.controller;

import com.example.chatapp.model.File;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    /**
     * 파일 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            FileService.FileUploadResult result = fileService.uploadFile(file, user.getId());

            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일 업로드 성공");
                
                Map<String, Object> fileData = new HashMap<>();
                fileData.put("_id", result.getFile().getId());
                fileData.put("filename", result.getFile().getFilename());
                fileData.put("originalname", result.getFile().getOriginalname());
                fileData.put("mimetype", result.getFile().getMimetype());
                fileData.put("size", result.getFile().getSize());
                fileData.put("uploadDate", result.getFile().getUploadDate());
                
                response.put("file", fileData);

                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 업로드에 실패했습니다.");
                return ResponseEntity.status(500).body(errorResponse);
            }

        } catch (Exception e) {
            log.error("파일 업로드 중 에러 발생", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 업로드 중 오류가 발생했습니다.");
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 보안이 강화된 파일 다운로드
     */
    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<?> downloadFile(
            @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            Resource resource = fileService.loadFileAsResourceSecurely(filename, user.getId());

            File fileEntity = fileRepository.findByFilename(filename)
                    .orElse(null);

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (IOException ex) {
                log.warn("Could not determine file type for: {}", filename);
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String originalFilename = fileEntity != null ? fileEntity.getOriginalname() : filename;
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            String contentDisposition = String.format(
                    "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                    originalFilename,
                    encodedFilename
            );

            long contentLength = fileEntity != null ? fileEntity.getSize() : resource.contentLength();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-cache, no-store, must-revalidate")
                    .body(resource);

        } catch (Exception e) {
            log.error("파일 다운로드 중 에러 발생: {}", filename, e);
            return handleFileError(e);
        }
    }

    /**
     * Node.js와 동일한 에러 처리
     */
    private ResponseEntity<?> handleFileError(Exception e) {
        String errorMessage = e.getMessage();
        int statusCode = 500;
        String responseMessage = "파일 처리 중 오류가 발생했습니다.";

        // Node.js errorResponses 매핑과 동일
        if (errorMessage != null) {
            if (errorMessage.contains("잘못된 파일명") || errorMessage.contains("Invalid filename")) {
                statusCode = 400;
                responseMessage = "잘못된 파일명입니다.";
            } else if (errorMessage.contains("인증") || errorMessage.contains("Authentication")) {
                statusCode = 401;
                responseMessage = "인증이 필요합니다.";
            } else if (errorMessage.contains("잘못된 파일 경로") || errorMessage.contains("Invalid file path")) {
                statusCode = 400;
                responseMessage = "잘못된 파일 경로입니다.";
            } else if (errorMessage.contains("찾을 수 없습니다") || errorMessage.contains("not found")) {
                statusCode = 404;
                responseMessage = "파일을 찾을 수 없습니다.";
            } else if (errorMessage.contains("메시지를 찾을 수 없습니다")) {
                statusCode = 404;
                responseMessage = "파일 메시지를 찾을 수 없습니다.";
            } else if (errorMessage.contains("권한") || errorMessage.contains("Unauthorized")) {
                statusCode = 403;
                responseMessage = "파일에 접근할 권한이 없습니다.";
            }
        }

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("success", false);
        errorResponse.put("message", responseMessage);

        return ResponseEntity.status(statusCode).body(errorResponse);
    }

    /**
     * 파일 미리보기 (Node.js 스펙과 동일)
     */
    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<?> viewFile(
            @PathVariable String filename,
            HttpServletRequest request,
            Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            Resource resource = fileService.loadFileAsResourceSecurely(filename, user.getId());

            File fileEntity = fileRepository.findByFilename(filename)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            // Node.js isPreviewable() 검증
            if (!fileEntity.isPreviewable()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "미리보기를 지원하지 않는 파일 형식입니다.");
                return ResponseEntity.status(415).body(errorResponse);
            }

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (IOException ex) {
                log.warn("Could not determine file type for: {}", filename);
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String originalFilename = fileEntity.getOriginalname();
            String encodedFilename = URLEncoder.encode(originalFilename, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            String contentDisposition = String.format(
                    "inline; filename=\"%s\"; filename*=UTF-8''%s",
                    originalFilename,
                    encodedFilename
            );

            long contentLength = fileEntity.getSize();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                    .body(resource);

        } catch (Exception e) {
            log.error("파일 미리보기 중 에러 발생: {}", filename, e);
            return handleFileError(e);
        }
    }

    /**
     * 안전한 파일 삭제 (Node.js 스펙과 동일)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable String id, Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            boolean deleted = fileService.deleteFileSecurely(id, user.getId());

            if (deleted) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "파일이 삭제되었습니다.");
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일 삭제에 실패했습니다.");
                return ResponseEntity.status(400).body(errorResponse);
            }

        } catch (RuntimeException e) {
            log.error("파일 삭제 중 에러 발생: {}", id, e);
            String errorMessage = e.getMessage();
            
            // Node.js와 동일한 에러 처리
            if (errorMessage != null && errorMessage.contains("찾을 수 없습니다")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 찾을 수 없습니다.");
                return ResponseEntity.status(404).body(errorResponse);
            } else if (errorMessage != null && errorMessage.contains("권한")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "파일을 삭제할 권한이 없습니다.");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "파일 삭제 중 오류가 발생했습니다.");
            errorResponse.put("error", errorMessage);
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
