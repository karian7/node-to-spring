package com.example.chatapp.controller;

import com.example.chatapp.dto.UpdateProfileRequest;
import com.example.chatapp.dto.UserProfileResponse;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

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
}
