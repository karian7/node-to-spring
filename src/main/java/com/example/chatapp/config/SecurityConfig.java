package com.example.chatapp.config;

import com.example.chatapp.security.JwtRequestFilter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final List<String> CORS_ALLOWED_ORIGINS = List.of(
            "https://bootcampchat-fe.run.goorm.site",
            "https://bootcampchat-hgxbv.dev-k8s.arkain.io",
            "http://localhost:3000",
            "http://localhost:3001",
            "http://localhost:3002",
            "https://localhost:3000",
            "https://localhost:3001",
            "https://localhost:3002",
            "http://0.0.0.0:3000",
            "https://0.0.0.0:3000"
    );

    private static final List<String> CORS_ALLOWED_HEADERS = List.of(
            "Content-Type",
            "Authorization",
            "x-auth-token",
            "x-session-id",
            "Cache-Control",
            "Pragma"
    );

    private static final List<String> CORS_EXPOSED_HEADERS = List.of(
            "x-auth-token",
            "x-session-id"
    );

    private static final List<String> CORS_ALLOWED_METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(request -> createCorsConfiguration()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfiguration createCorsConfiguration() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(CORS_ALLOWED_ORIGINS);
        config.setAllowedMethods(CORS_ALLOWED_METHODS);
        config.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        config.setExposedHeaders(CORS_EXPOSED_HEADERS);
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1).getSeconds());
        return config;
    }
}
