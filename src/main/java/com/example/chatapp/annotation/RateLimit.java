package com.example.chatapp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 허용되는 최대 요청 수
     */
    int maxRequests() default 60;

    /**
     * 시간 윈도우 (초 단위)
     */
    int windowSeconds() default 60;

    /**
     * Rate Limit 적용 범위
     * IP: IP 주소별
     * USER: 인증된 사용자별
     * IP_AND_USER: IP + 사용자별
     */
    LimitScope scope() default LimitScope.IP;

    /**
     * Rate Limit 초과 시 에러 메시지
     */
    String message() default "너무 많은 요청이 발생했습니다. 잠시 후 다시 시도해주세요.";

    enum LimitScope {
        IP,
        USER,
        IP_AND_USER
    }
}
