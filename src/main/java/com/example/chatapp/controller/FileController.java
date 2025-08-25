package com.example.chatapp.controller;

import com.example.chatapp.dto.ApiResponse;
import com.example.chatapp.dto.FileUploadResponse;
import com.example.chatapp.model.File;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.FileRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    /**
     * 향상된 파일 업로드 (RAG 연동 + 메타데이터 강화)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "roomId", required = false) String roomId,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            // 완전한 파일 업로드 처리 (보안 검증 + RAG 연동 + 메타데이터)
            FileService.EnhancedFileUploadResult result =
                    fileService.uploadFileComplete(file, user.getId(), roomId);

            if (result.isSuccess()) {
                FileUploadResponse response = new FileUploadResponse(
                        result.getFile().getFilename(),
                        result.getFile().getOriginalname(),
                        result.getFile().getMimetype(),
                        result.getFile().getSize(),
                        result.getDownloadUrl(),
                        result.isRagProcessed()
                );

                return ResponseEntity.ok(ApiResponse.success("파일이 성공적으로 업로드되었습니다.", response));
            } else {
                return ResponseEntity.status(500).body(
                        ApiResponse.error("파일 업로드에 실패했습니다.")
                );
            }

        } catch (Exception e) {
            log.error("파일 업로드 중 에러 발생", e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("파일 업로드 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    /**
     * 보안이 강화된 파일 다운로드
     */
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName, HttpServletRequest request, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            // 보안 검증이 포함된 파일 로드
            Resource resource = fileService.loadFileAsResourceSecurely(fileName, user.getId());

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (IOException ex) {
                log.warn("Could not determine file type for: {}", fileName);
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("파일 다운로드 중 에러 발생: {}", fileName, e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 안전한 파일 삭제
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteFile(@PathVariable String fileId, Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            boolean deleted = fileService.deleteFileSecurely(fileId, user.getId());

            if (deleted) {
                return ResponseEntity.ok(ApiResponse.success("파일이 성공적으로 삭제되었습니다."));
            } else {
                return ResponseEntity.status(400).body(
                        ApiResponse.error("파일 삭제에 실패했습니다.")
                );
            }

        } catch (Exception e) {
            log.error("파일 삭제 중 에러 발생: {}", fileId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("파일 삭제 중 오류가 발생했습니다: " + e.getMessage())
            );
        }
    }

    /**
     * 사용자별 업로드한 파일 목록 조회
     */
    @GetMapping("/my-files")
    public ResponseEntity<?> getMyFiles(Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getName()));

            List<File> files = fileService.getUserFiles(user.getId());
            return ResponseEntity.ok(ApiResponse.success("파일 목록을 성공적으로 조회했습니다.", files));

        } catch (Exception e) {
            log.error("사용자 파일 목록 조회 중 에러 발생", e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("파일 목록 조회 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * 룸별 파일 목록 조회
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<?> getRoomFiles(@PathVariable String roomId, Principal principal) {
        try {
            // TODO: 룸 참여자인지 권한 검증 추가 필요

            List<File> files = fileService.getRoomFiles(roomId);
            return ResponseEntity.ok(ApiResponse.success("룸 파일 목록을 성공적으로 조회했습니다.", files));

        } catch (Exception e) {
            log.error("룸 파일 목록 조회 중 에러 발생: {}", roomId, e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("룸 파일 목록 조회 중 오류가 발생했습니다.")
            );
        }
    }

    /**
     * RAG 처리된 파일 목록 조회 (AI 기능용)
     */
    @GetMapping("/rag-processed")
    public ResponseEntity<?> getRagProcessedFiles(Principal principal) {
        try {
            List<File> files = fileRepository.findByRagProcessedTrue();
            return ResponseEntity.ok(ApiResponse.success("RAG 처리된 파일 목록을 조회했습니다.", files));

        } catch (Exception e) {
            log.error("RAG 처리 파일 목록 조회 중 에러 발생", e);
            return ResponseEntity.status(500).body(
                    ApiResponse.error("RAG 처리 파일 목록 조회 중 오류가 발생했습니다.")
            );
        }
    }
}
