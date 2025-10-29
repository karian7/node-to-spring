package com.example.chatapp.config;

import com.example.chatapp.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitConfig.RateLimitService rateLimitService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (true) {
            return true; // 임시로 모든 요청 허용
        }

        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 메서드 레벨 @RateLimit 어노테이션 확인
        RateLimit methodRateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

        // 클래스 레벨 @RateLimit 어노테이션 확인
        RateLimit classRateLimit = handlerMethod.getBeanType().getAnnotation(RateLimit.class);

        // 메서드 레벨이 우선, 없으면 클래스 레벨 사용
        RateLimit rateLimit = methodRateLimit != null ? methodRateLimit : classRateLimit;

        if (rateLimit == null) {
            return true; // Rate Limit 어노테이션이 없으면 통과
        }

        // Rate Limit 설정 추출
        int maxRequests = rateLimit.maxRequests();
        Duration window = Duration.ofSeconds(rateLimit.windowSeconds());

        // 사용자별 Rate Limit 적용을 위한 클라이언트 ID 생성
        String clientId = generateClientId(request, rateLimit.scope());

        // Rate Limit 체크 (커스텀 클라이언트 ID 사용)
        return checkRateLimitWithCustomId(request, response, clientId, maxRequests, window);
    }

    /**
     * Rate Limit 범위에 따른 클라이언트 ID 생성
     */
    private String generateClientId(HttpServletRequest request, RateLimit.LimitScope scope) {
        String clientIp = getClientIpAddress(request);

        switch (scope) {
            case USER:
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                    return "user:" + auth.getName();
                }
                // 인증되지 않은 사용자는 IP 기반으로 fallback
                return "ip:" + clientIp;

            case IP_AND_USER:
                Authentication userAuth = SecurityContextHolder.getContext().getAuthentication();
                if (userAuth != null && userAuth.isAuthenticated() && !userAuth.getName().equals("anonymousUser")) {
                    return "ip_user:" + clientIp + ":" + userAuth.getName();
                }
                return "ip:" + clientIp;

            case IP:
            default:
                return "ip:" + clientIp;
        }
    }

    /**
     * 커스텀 클라이언트 ID로 Rate Limit 체크
     */
    private boolean checkRateLimitWithCustomId(HttpServletRequest request, HttpServletResponse response,
                                             String clientId, int maxRequests, Duration window) throws Exception {

        String key = "rate_limit:" + clientId;

        try {
            // Redis를 사용한 Rate Limit 체크 로직을 직접 구현
            return performRateLimitCheck(request, response, key, maxRequests, window);

        } catch (Exception e) {
            log.error("Rate limit check failed for client: {}", clientId, e);
            // Rate Limit 체크 실패 시 요청 허용 (Fail Open)
            return true;
        }
    }

    /**
     * 실제 Rate Limit 체크 수행
     */
    private boolean performRateLimitCheck(HttpServletRequest request, HttpServletResponse response,
                                        String key, int maxRequests, Duration window) throws Exception {

        // RateLimitService의 기존 로직을 활용하되, 커스텀 키 사용
        // 임시로 기본 체크 사용 (추후 개선 가능)
        return rateLimitService.isAllowed(request, response, maxRequests, window);
    }

    /**
     * 클라이언트 IP 주소 추출
     */
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
}
