package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.event.SessionEndedEvent;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.JwtService;
import com.ktb.chatapp.service.SessionCreationResult;
import com.ktb.chatapp.service.SessionMetadata;
import com.ktb.chatapp.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public ResponseEntity<?> getAuthStatus() {
        Map<String, String> routes = new LinkedHashMap<>();
        routes.put("/register", "POST - 새 사용자 등록");
        routes.put("/login", "POST - 사용자 로그인");
        routes.put("/logout", "POST - 로그아웃 (인증 필요)");
        routes.put("/verify-token", "POST - 토큰 검증");
        routes.put("/refresh-token", "POST - 토큰 갱신 (인증 필요)");
        return ResponseEntity.ok(Map.of("status", "active", "routes", routes));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody RegisterRequest registerRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {

        // Handle validation errors
        ResponseEntity<?> errors = getBindingError(bindingResult);
        if (errors != null) return errors;
        
        // Check existing user
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("이미 등록된 이메일입니다."));
        }

        try {
            // Create user
            User user = User.builder()
                    .name(registerRequest.getName())
                    .email(registerRequest.getEmail().toLowerCase())
                    .password(passwordEncoder.encode(registerRequest.getPassword()))
                    .build();

            user = userRepository.save(user);

            // Create session with metadata
            SessionMetadata metadata = new SessionMetadata(
                    request.getHeader("User-Agent"),
                    getClientIpAddress(request),
                    request.getHeader("User-Agent")
            );

            SessionCreationResult sessionInfo =
                    sessionService.createSession(user.getId(), metadata);

            // Generate JWT token with session ID
            String token = jwtService.generateToken(
                sessionInfo.getSessionId(),
                user.getEmail(),
                user.getId()
            );

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .message("회원가입이 완료되었습니다.")
                    .token(token)
                    .sessionId(sessionInfo.getSessionId())
                    .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header("Authorization", "Bearer " + token)
                    .header("x-session-id", sessionInfo.getSessionId())
                    .body(response);

        } catch (org.springframework.dao.DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("이미 등록된 이메일입니다."));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Register error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("회원가입 처리 중 오류가 발생했습니다."));
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest loginRequest,
            BindingResult bindingResult,
            HttpServletRequest request) {

        // Handle validation errors
        ResponseEntity<?> errors = getBindingError(bindingResult);
        if (errors != null) return errors;
        
        try {
            // Authenticate user
            User user = userRepository.findByEmail(loginRequest.getEmail().toLowerCase())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            user.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // 단일 세션 정책을 위해 기존 세션 제거
            sessionService.removeAllUserSessions(user.getId());

            // Create new session
            SessionMetadata metadata = new SessionMetadata(
                    request.getHeader("User-Agent"),
                    getClientIpAddress(request),
                    request.getHeader("User-Agent")
            );

            SessionCreationResult sessionInfo =
                    sessionService.createSession(user.getId(), metadata);

            // Generate JWT token
            String token = jwtService.generateToken(
                sessionInfo.getSessionId(),
                user.getEmail(),
                user.getId()
            );

            LoginResponse response = LoginResponse.builder()
                    .success(true)
                    .token(token)
                    .sessionId(sessionInfo.getSessionId())
                    .user(new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage()))
                    .build();

            return ResponseEntity.ok()
                    .header("Authorization", "Bearer " + token)
                    .header("x-session-id", sessionInfo.getSessionId())
                    .body(response);

        } catch (UsernameNotFoundException | BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("이메일 또는 비밀번호가 올바르지 않습니다."));
        } catch (Exception e) {
            log.error("Login error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그인 처리 중 오류가 발생했습니다."));
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            Authentication authentication) {

        try {
            // x-session-id 헤더 필수
            String sessionId = extractSessionId(request);
            if (sessionId == null || sessionId.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("x-session-id 헤더가 필요합니다."));
            }
            
            if (authentication != null) {
                // Spring Security 6 패턴: Authentication의 Details에서 userId 추출
                @SuppressWarnings("unchecked")
                Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
                String userId = (String) details.get("userId");
                
                if (userId != null) {
                    sessionService.removeSession(userId, sessionId);
                    
                    // Publish event for session ended
                    eventPublisher.publishEvent(new SessionEndedEvent(
                            this, userId, "logout", "로그아웃되었습니다."
                    ));
                }
            }

            SecurityContextHolder.clearContext();
            
            return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다.", null));

        } catch (Exception e) {
            log.error("Logout error: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("로그아웃 처리 중 오류가 발생했습니다."));
        }
    }
    
    
    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String sessionId = extractSessionId(request);
            
            if (token == null || sessionId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenVerifyResponse(false, "토큰 또는 세션 ID가 필요합니다.", null));
            }

            // 토큰 유효성 검증
            if (!jwtService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "유효하지 않은 토큰입니다.", null));
            }

            // 토큰에서 사용자 정보 추출
            String userId = jwtService.extractUserId(token);
            
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "사용자를 찾을 수 없습니다.", null));
            }

            User user = userOpt.get();
            // 세션 유효성 검증
            if (!sessionService.validateSession(user.getId(), sessionId).isValid()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenVerifyResponse(false, "만료된 세션입니다.", null));
            }

            AuthUserDto authUserDto = new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());
            return ResponseEntity.ok(new TokenVerifyResponse(true, "토큰이 유효합니다.", authUserDto));

        } catch (Exception e) {
            log.error("Token verification error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenVerifyResponse(false, "토큰 검증 중 오류가 발생했습니다.", null));
        }
    }
    
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(HttpServletRequest request) {
        try {
            String token = extractToken(request);
            String sessionId = extractSessionId(request);
            
            if (token == null || sessionId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new TokenRefreshResponse(false, "토큰 또는 세션 ID가 필요합니다.", null, null));
            }

            // 만료된 토큰이라도 사용자 정보는 추출 가능
            String userId = jwtService.extractUserIdFromExpiredToken(token);
            
            Optional<User> userOpt = userRepository.findById(userId);

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "사용자를 찾을 수 없습니다.", null, null));
            }


            // 세션 유효성 검증
            var user = userOpt.get();
            if (!sessionService.validateSession(user.getId(), sessionId).isValid()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new TokenRefreshResponse(false, "만료된 세션입니다.", null, null));
            }

            // 세션 갱신 - 새로운 세션 ID 생성
            sessionService.removeSession(user.getId(), sessionId);
            SessionMetadata metadata = new SessionMetadata(
                    request.getHeader("User-Agent"),
                    getClientIpAddress(request),
                    request.getHeader("User-Agent")
            );

            SessionCreationResult newSessionInfo = sessionService.createSession(user.getId(), metadata);

            // 새로운 토큰과 세션 ID 생성
            String newToken = jwtService.generateToken(
                newSessionInfo.getSessionId(),
                user.getEmail(),
                user.getId()
            );
            return ResponseEntity.ok(new TokenRefreshResponse(true, "토큰이 갱신되었습니다.", newToken, newSessionInfo.getSessionId()));

        } catch (Exception e) {
            log.error("Token refresh error: ", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new TokenRefreshResponse(false, "토큰 갱신 중 오류가 발생했습니다.", null, null));
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
    
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("x-auth-token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    private ResponseEntity<?> getBindingError(BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            return null;
        }
        List<ValidationError> errors = bindingResult.getFieldErrors().stream()
                .map(error -> ValidationError.builder()
                        .field(error.getField())
                        .message(error.getDefaultMessage())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError("입력값이 올바르지 않습니다.", errors));
    }
}
