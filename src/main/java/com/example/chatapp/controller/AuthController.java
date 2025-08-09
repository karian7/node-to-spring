package com.example.chatapp.controller;

import com.example.chatapp.dto.AuthResponse;
import com.example.chatapp.dto.LoginRequest;
import com.example.chatapp.dto.RegisterRequest;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.util.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.chatapp.service.SessionService;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SessionService sessionService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        User user = new User();
        user.setName(registerRequest.getName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully!");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
        );

        final UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getEmail());
        final User user = userRepository.findByEmail(loginRequest.getEmail()).get();

        // Handle duplicate login
        String existingSession = sessionService.getSession(user.getId());
        if (existingSession != null) {
            // For simplicity, we just remove the old session.
            // A more advanced implementation could notify the old session's client.
            sessionService.removeSession(user.getId());
        }

        String sessionId = sessionService.createSession(user.getId());
        final String jwt = jwtUtil.generateToken(userDetails, sessionId);

        return ResponseEntity.ok(new AuthResponse(jwt));
    }

    @GetMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String tokenHeader, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String jwt = tokenHeader.substring(7);
        String sessionId = jwtUtil.extractClaim(jwt, (Claims c) -> c.get("sessionId", String.class));

        User user = userRepository.findByEmail(principal.getName()).get();
        if (!sessionService.isSessionValid(user.getId(), sessionId)) {
            return ResponseEntity.status(401).body("Invalid session");
        }

        sessionService.refreshSession(user.getId());

        UserDetails userDetails = userDetailsService.loadUserByUsername(principal.getName());
        return ResponseEntity.ok(userDetails);
    }
}
