package com.example.chatapp.controller;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final SessionService sessionService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {

        // Handle validation errors
        if (bindingResult.hasErrors()) {
            Map<String, String> errors = new HashMap<>();
            for (FieldError error : bindingResult.getFieldErrors()) {
                errors.put(error.getField(), error.getDefaultMessage());
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("입력값이 올바르지 않습니다.", errors));
        }

        // Check existing user
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("이미 등록된 이메일입니다."));
        }

        try {
            // Create user
            User user = User.builder()
                    .name(registerRequest.getName())
                    .email(registerRequest.getEmail())
                    .password(passwordEncoder.encode(registerRequest.getPassword()))
                    .build();

            userRepository.save(user);

            // Create session with metadata
            SessionService.SessionMetadata metadata = new SessionService.SessionMetadata(
                    request.getHeader("User-Agent"),
                    getClientIpAddress(request),
                    request.getHeader("User-Agent")
            );

            SessionService.SessionCreationResult sessionInfo =
                    sessionService.createSession(user.getId(), metadata);

            // Generate JWT token with session ID
            String token = jwtUtil.generateToken(user.getEmail());

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("sessionId", sessionInfo.getSessionId());
            responseData.put("user", Map.of(
                    "_id", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail()
            ));

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("회원가입이 완료되었습니다.", responseData));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {

        // Handle validation errors
        if (bindingResult.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("이메일과 비밀번호를 입력해주세요."));
        }

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Get user details
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Check for existing session
            SessionService.SessionData existingSession = sessionService.getActiveSession(user.getId());
            if (existingSession != null) {
                // Remove existing session (single session per user like Node.js)
                sessionService.removeAllUserSessions(user.getId());
            }

            // Create new session
            SessionService.SessionMetadata metadata = new SessionService.SessionMetadata(
                    request.getHeader("User-Agent"),
                    getClientIpAddress(request),
                    request.getHeader("User-Agent")
            );

            SessionService.SessionCreationResult sessionInfo =
                    sessionService.createSession(user.getId(), metadata);

            // Generate JWT token
            String token = jwtUtil.generateToken(user.getEmail());

            // Prepare response data
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("sessionId", sessionInfo.getSessionId());
            responseData.put("user", Map.of(
                    "_id", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail()
            ));

            return ResponseEntity.ok(ApiResponse.success("로그인이 완료되었습니다.", responseData));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            Authentication authentication) {

        try {
            if (authentication != null) {
                String email = authentication.getName();
                User user = userRepository.findByEmail(email).orElse(null);

                if (user != null) {
                    String sessionId = extractSessionId(request);
                    sessionService.removeSession(user.getId(), sessionId);
                }
            }

            SecurityContextHolder.clearContext();
            return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다.", null));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그아웃 처리 중 오류가 발생했습니다."));
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private String extractSessionId(HttpServletRequest request) {
        String sessionId = request.getHeader("x-session-id");
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        return request.getParameter("sessionId");
    }
}
