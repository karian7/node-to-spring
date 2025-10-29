package com.example.chatapp.controller;

import com.example.chatapp.annotation.RateLimit;
import com.example.chatapp.dto.*;
import com.example.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
@Slf4j
public class UserController {

    private final UserService userService;

    /**
     * 현재 사용자 프로필 조회
     */
    @GetMapping("/profile")
    @RateLimit
    public ResponseEntity<?> getCurrentUserProfile(Principal principal) {
        try {
            UserResponse response = userService.getCurrentUserProfile(principal.getName());
            return ResponseEntity.ok(new UserApiResponse(response));
        } catch (UsernameNotFoundException e) {
            log.error("사용자 프로필 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자 프로필 조회 실패 - 사용자를 찾을 수 없습니다: " + e.getMessage()));
        } catch (Exception e) {
            log.error("사용자 프로필 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("사용자 프로필 조회 중 서버 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 현재 사용자 프로필 업데이트
     */
    @PutMapping("/profile")
    @RateLimit
    public ResponseEntity<?> updateCurrentUserProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest updateRequest) {

        try {
            UserResponse response = userService.updateUserProfile(principal.getName(), updateRequest);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (UsernameNotFoundException e) {
            log.error("사용자 프로필 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자 프로필 업데이트 실패 - 사용자를 찾을 수 없습니다: " + e.getMessage()));
        } catch (Exception e) {
            log.error("사용자 프로필 업데이트 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("사용자 프로필 업데이트 중 서버 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 프로필 이미지 업로드
     */
    @PostMapping("/profile-image")
    @RateLimit(maxRequests = 10) // 프로필 이미지는 더 제한적으로
    public ResponseEntity<?> uploadProfileImage(
            Principal principal,
            @RequestParam("profileImage") MultipartFile file) {

        try {
            ProfileImageResponse response = userService.uploadProfileImage(principal.getName(), file);
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException e) {
            log.error("프로필 이미지 업로드 실패 - 사용자 없음: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("프로필 이미지 업로드 실패 - 사용자를 찾을 수 없습니다: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            log.error("프로필 이미지 업로드 실패 - 잘못된 입력: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("프로필 이미지 업로드 실패 - 잘못된 입력: " + e.getMessage()));
        } catch (Exception e) {
            log.error("프로필 이미지 업로드 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("프로필 이미지 업로드 중 서버 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @Data
    static class UserApiResponse {
        private boolean success;
        private UserResponse user;

        public UserApiResponse(UserResponse user) {
            this.user = user;
            this.success = true;
        }
    }
}

