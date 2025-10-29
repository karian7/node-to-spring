package com.example.chatapp.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadsPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String resourceLocation = uploadsPath.toUri().toString();
        if (!resourceLocation.endsWith("/")) {
            resourceLocation = resourceLocation + "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resourceLocation);
    }
}
