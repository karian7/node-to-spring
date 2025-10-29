package com.example.chatapp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**") // API 경로에만 Rate Limiting 적용
                .excludePathPatterns(
                    "/api/auth/login",  // 로그인은 별도 처리
                    "/api/auth/register", // 회원가입은 별도 처리
                    "/api/health"       // Health Check는 제외
                );
    }
}
