package com.example.chatapp.controller;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.FileService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileService fileService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUserProfile(Principal principal) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

        UserProfileResponse profileResponse = new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getProfileImage()
        );
        return ResponseEntity.ok(profileResponse);
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUserProfile(Principal principal, @Valid @RequestBody UpdateProfileRequest updateRequest) {
        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

        user.setName(updateRequest.getName());
        User updatedUser = userRepository.save(user);

        UserProfileResponse profileResponse = new UserProfileResponse(
                updatedUser.getId(),
                updatedUser.getName(),
                updatedUser.getEmail(),
                updatedUser.getProfileImage()
        );
        return ResponseEntity.ok(profileResponse);
    }

    @PostMapping("/me/profile-image")
    public ResponseEntity<?> uploadProfileImage(Principal principal, @RequestParam("file") MultipartFile file) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 파일 업로드 처리
            String profileImageUrl = fileService.uploadProfileImage(file, user.getId());

            // 사용자 프로필 이미지 URL 업데이트
            user.setProfileImage(profileImageUrl);
            User updatedUser = userRepository.save(user);

            UserProfileResponse profileResponse = new UserProfileResponse(
                    updatedUser.getId(),
                    updatedUser.getName(),
                    updatedUser.getEmail(),
                    updatedUser.getProfileImage()
            );

            return ResponseEntity.ok(profileResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "프로필 이미지 업로드에 실패했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchUsers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        try {
            Pageable pageable = PageRequest.of(page, size);

            // 현재 사용자 제외하고 검색
            Page<User> userPage = userRepository.findByNameContainingIgnoreCaseAndEmailNot(
                    query, principal.getName(), pageable);

            List<UserSummaryResponse> userSummaries = userPage.getContent().stream()
                    .map(user -> new UserSummaryResponse(
                            user.getId(),
                            user.getName(),
                            user.getEmail(),
                            user.getProfileImage()))
                    .collect(Collectors.toList());

            UserSearchResponse searchResponse = new UserSearchResponse(
                    true,
                    "사용자 검색 완료",
                    userSummaries,
                    userPage.getTotalPages(),
                    userPage.getTotalElements(),
                    page
            );

            return ResponseEntity.ok(searchResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "사용자 검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        try {
            Pageable pageable = PageRequest.of(page, size);

            // 현재 사용자 제외하고 모든 사용자 조회
            Page<User> userPage = userRepository.findByEmailNot(principal.getName(), pageable);

            List<UserSummaryResponse> userSummaries = userPage.getContent().stream()
                    .map(user -> new UserSummaryResponse(
                            user.getId(),
                            user.getName(),
                            user.getEmail(),
                            user.getProfileImage()))
                    .collect(Collectors.toList());

            UserSearchResponse usersResponse = new UserSearchResponse(
                    true,
                    "사용자 목록 조회 완료",
                    userSummaries,
                    userPage.getTotalPages(),
                    userPage.getTotalElements(),
                    page
            );

            return ResponseEntity.ok(usersResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "사용자 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String userId, Principal principal) {
        try {
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(false, "사용자를 찾을 수 없습니다."));
            }

            User user = userOpt.get();
            UserProfileResponse profileResponse = new UserProfileResponse(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getProfileImage()
            );

            return ResponseEntity.ok(profileResponse);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "사용자 프로필 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }
}
