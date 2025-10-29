package com.example.chatapp.controller;

import com.example.chatapp.dto.ApiResponse;
import com.example.chatapp.dto.ProfileImageResponse;
import com.example.chatapp.dto.UpdateProfileRequest;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.Instant;

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
    public ResponseEntity<?> getCurrentUserProfile(Principal principal) {
        try {
            UserResponse response = userService.getCurrentUserProfile(principal.getName());
            return ResponseEntity.ok(new UserApiResponse(response));
        } catch (UsernameNotFoundException e) {
            log.error("사용자 프로필 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("사용자 프로필 조회 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("프로필 조회 중 오류가 발생했습니다."));
        }
    }

    /**
     * 현재 사용자 프로필 업데이트
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateCurrentUserProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest updateRequest) {

        try {
            UserResponse response = userService.updateUserProfile(principal.getName(), updateRequest);
            return ResponseEntity.ok(new UserUpdateResponse("프로필이 업데이트되었습니다.", response));
        } catch (UsernameNotFoundException e) {
            log.error("사용자 프로필 업데이트 실패: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("사용자 프로필 업데이트 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("프로필 업데이트 중 오류가 발생했습니다."));
        }
    }

    /**
     * 프로필 이미지 업로드
     */
    @PostMapping("/profile-image")
    public ResponseEntity<?> uploadProfileImage(
            Principal principal,
            @RequestParam("profileImage") MultipartFile file) {

        try {
            ProfileImageResponse response = userService.uploadProfileImage(principal.getName(), file);
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException e) {
            log.error("프로필 이미지 업로드 실패 - 사용자 없음: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자를 찾을 수 없습니다."));
        } catch (IllegalArgumentException e) {
            log.error("프로필 이미지 업로드 실패 - 잘못된 입력: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("프로필 이미지 업로드 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("이미지 업로드 중 오류가 발생했습니다."));
        }
    }

    /**
     * 프로필 이미지 삭제
     */
    @DeleteMapping("/profile-image")
    public ResponseEntity<?> deleteProfileImage(Principal principal) {
        try {
            userService.deleteProfileImage(principal.getName());
            return ResponseEntity.ok(ApiResponse.success("프로필 이미지가 삭제되었습니다."));
        } catch (UsernameNotFoundException e) {
            log.error("프로필 이미지 삭제 실패 - 사용자 없음: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("프로필 이미지 삭제 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("프로필 이미지 삭제 중 오류가 발생했습니다."));
        }
    }

    /**
     * 회원 탈퇴
     */
    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(Principal principal) {
        try {
            userService.deleteUserAccount(principal.getName());
            return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다."));
        } catch (UsernameNotFoundException e) {
            log.error("회원 탈퇴 실패 - 사용자 없음: {}", e.getMessage());
            return ResponseEntity.status(404).body(ApiResponse.error("사용자를 찾을 수 없습니다."));
        } catch (Exception e) {
            log.error("회원 탈퇴 처리 중 오류 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("회원 탈퇴 처리 중 오류가 발생했습니다."));
        }
    }

    /**
     * API 상태 확인
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(new StatusResponse("User API is running"));
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

    @Data
    static class UserUpdateResponse {
        private final boolean success = true;
        private final String message;
        private final UserResponse user;

        UserUpdateResponse(String message, UserResponse user) {
            this.message = message;
            this.user = user;
        }
    }

    @Data
    static class StatusResponse {
        private final boolean success = true;
        private final String message;
        private final Instant timestamp;

        StatusResponse(String message) {
            this.message = message;
            this.timestamp = Instant.now();
        }
    }
}
