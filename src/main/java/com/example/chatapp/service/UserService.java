package com.example.chatapp.service;

import com.example.chatapp.dto.ProfileImageResponse;
import com.example.chatapp.dto.UpdateProfileRequest;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.util.FileSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FileService fileService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.profile.image.max-size:5242880}") // 5MB
    private long maxProfileImageSize;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    /**
     * 현재 사용자 프로필 조회
     */
    public UserResponse getCurrentUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트
     */
    public UserResponse updateUserProfile(String userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 프로필 정보 업데이트
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", userId, request.getName());

        return UserResponse.from(updatedUser);
    }

    /**
     * 프로필 이미지 업로드 (보안 강화)
     */
    public ProfileImageResponse uploadProfileImage(String userId, MultipartFile file) {
        // 사용자 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 파일 유효성 검증
        validateProfileImageFile(file);

        // 기존 프로필 이미지 삭제
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
        }

        // 새 파일 저장 (보안 검증 포함)
        String profileImageUrl = fileService.storeFileSecurely(file, "profiles");

        // 사용자 프로필 이미지 URL 업데이트
        user.setProfileImage(profileImageUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("프로필 이미지 업로드 완료 - User ID: {}, File: {}", userId, profileImageUrl);

        return new ProfileImageResponse(
                true,
                "프로필 이미지가 업데이트되었습니다.",
                profileImageUrl
        );
    }

    /**
     * 특정 사용자 프로필 조회
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.from(user);
    }

    /**
     * 프로필 이미지 파일 유효성 검증
     */
    private void validateProfileImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지가 제공되지 않았습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > maxProfileImageSize) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        // Content-Type 검증 - Node.js처럼 image/* 전체 허용
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // 파일 확장자 검증 (보안을 위해 화이트리스트 유지)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // FileSecurityUtil의 static 메서드 호출
        String extension = FileSecurityUtil.getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    /**
     * 기존 프로필 이미지 삭제
     */
    private void deleteOldProfileImage(String profileImageUrl) {
        try {
            if (profileImageUrl != null && profileImageUrl.startsWith("/uploads/")) {
                // URL에서 파일명 추출
                String filename = profileImageUrl.substring("/uploads/".length());
                Path filePath = Paths.get(uploadDir, filename);

                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                    log.info("기존 프로필 이미지 삭제 완료: {}", filename);
                }
            }
        } catch (IOException e) {
            log.warn("기존 프로필 이미지 삭제 실패: {}", e.getMessage());
        }
    }

    /**
     * 프로필 이미지 삭제
     */
    public void deleteProfileImage(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
            user.setProfileImage("");
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("프로필 이미지 삭제 완료 - User ID: {}", userId);
        }
    }

    /**
     * 회원 탈퇴 처리
     */
    public void deleteUserAccount(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            deleteOldProfileImage(user.getProfileImage());
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", userId);
    }
}
