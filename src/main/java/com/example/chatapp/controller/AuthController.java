package com.example.chatapp.controller;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.SessionService;
import com.example.chatapp.util.JwtUtil;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(false, "이미 등록된 이메일입니다."));
        }

        User user = User.builder()
                .name(registerRequest.getName())
                .email(registerRequest.getEmail())
                .password(passwordEncoder.encode(registerRequest.getPassword()))
                .build();

        userRepository.save(user);

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(user.getEmail(), user.getPassword(),
                new ArrayList<>());

        final String sessionId = sessionService.createSession(user.getId());
        final String token = jwtUtil.generateToken(userDetails, sessionId);

        UserDto userDto = new UserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RegisterResponse(true, "회원가입이 완료되었습니다.", token, sessionId, userDto));
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(false, "사용자를 찾을 수 없습니다."));
        }

        User user = userOpt.get();

        final String sessionId = sessionService.createSession(user.getId());
        final String token = jwtUtil.generateToken(userDetails, sessionId);

        return ResponseEntity.ok(new LoginResponse(true, token, sessionId));
    }

    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestBody TokenVerifyRequest tokenVerifyRequest) {
        try {
            String token = tokenVerifyRequest.getToken();
            String sessionId = tokenVerifyRequest.getSessionId();

            // 토큰 유효성 검증
            if (!jwtUtil.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "유효하지 않은 토큰입니다.", null));
            }

            // 세션 유효성 검증
            if (!sessionService.isValidSession(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "만료된 세션입니다.", null));
            }

            // 토큰에서 사용자 정보 추출
            String email = jwtUtil.getUsernameFromToken(token);
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "사용자를 찾을 수 없습니다.", null));
            }

            User user = userOpt.get();
            UserDto userDto = new UserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());

            return ResponseEntity.ok(new TokenVerifyResponse(true, "토큰이 유효합니다.", userDto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenVerifyResponse(false, "토큰 검증 중 오류가 발생했습니다.", null));
        }
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@RequestBody TokenRefreshRequest tokenRefreshRequest) {
        try {
            String token = tokenRefreshRequest.getToken();
            String sessionId = tokenRefreshRequest.getSessionId();

            // 세션 유효성 검증
            if (!sessionService.isValidSession(sessionId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "만료된 세션입니다.", null, null));
            }

            // 만료된 토큰이라도 사용자 정보는 추출 가능
            String email = jwtUtil.getUsernameFromToken(token);
            Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "사용자를 찾을 수 없습니다.", null, null));
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // 새로운 토큰 생성
            String newToken = jwtUtil.generateToken(userDetails, sessionId);

            // 세션 갱신
            sessionService.refreshSessionById(sessionId);

            return ResponseEntity.ok(new TokenRefreshResponse(true, "토큰이 갱신되었습니다.", newToken, sessionId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenRefreshResponse(false, "토큰 갱신 중 오류가 발생했습니다.", null, null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody LogoutRequest logoutRequest) {
        try {
            String sessionId = logoutRequest.getSessionId();

            // 세션 무효화
            boolean loggedOut = sessionService.invalidateSession(sessionId);

            if (loggedOut) {
                // Spring Security 컨텍스트 클리어
                SecurityContextHolder.clearContext();

                return ResponseEntity.ok(new AuthResponse(true, "로그아웃이 완료되었습니다."));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new AuthResponse(false, "유효하지 않은 세션입니다."));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AuthResponse(false, "로그아웃 처리 중 오류가 발생했습니다."));
        }
    }
}
