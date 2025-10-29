package com.example.chatapp.controller;

import com.example.chatapp.dto.UpdateProfileRequest;
import com.example.chatapp.dto.UserResponse;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserController userController;

    private User user;
    private Principal principal;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId("mock-user-id");
        user.setName("Test User");
        user.setEmail("test@example.com");
        user.setProfileImage("image.jpg");

        principal = mock(Principal.class);
        when(principal.getName()).thenReturn("test@example.com");
    }

    @Test
    void testGetCurrentUserProfile() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = userController.getCurrentUserProfile(principal);

        assertEquals(200, response.getStatusCodeValue());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("Test User", body.getName());
    }

    @Test
    void testUpdateCurrentUserProfile() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName("New Name");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setName("New Name");
            return savedUser;
        });

        ResponseEntity<?> response = userController.updateCurrentUserProfile(principal, request);

        assertEquals(200, response.getStatusCodeValue());
        UserResponse body = (UserResponse) response.getBody();
        assertEquals("New Name", body.getName());
    }
}
