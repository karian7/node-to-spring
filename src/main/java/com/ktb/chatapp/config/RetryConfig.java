package com.ktb.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Retry 설정
 * 메시지 로드 등의 재시도 로직을 위한 RetryTemplate 구성
 */
@Slf4j
@Configuration
public class RetryConfig {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_INTERVAL = 2000L; // 2초
    private static final double MULTIPLIER = 2.0; // 지수 배수
    private static final long MAX_INTERVAL = 10000L; // 10초

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 재시도 정책 설정
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(MAX_ATTEMPTS);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 지수 백오프 정책 설정
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(INITIAL_INTERVAL);
        backOffPolicy.setMultiplier(MULTIPLIER);
        backOffPolicy.setMaxInterval(MAX_INTERVAL);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // 재시도 리스너 등록 (재시도 전후 로깅)
        retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(
                    org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback) {
                if (context.getRetryCount() > 0) {
                    // 재시도 시작 시 대기 시간 계산
                    long waitTime = calculateBackoffTime(context.getRetryCount());
                    log.debug("Retry attempt {} will start after {}ms",
                            context.getRetryCount() + 1, waitTime);
                }
                return true;
            }

            @Override
            public <T, E extends Throwable> void onError(
                    org.springframework.retry.RetryContext context,
                    org.springframework.retry.RetryCallback<T, E> callback,
                    Throwable throwable) {
                long waitTime = calculateBackoffTime(context.getRetryCount());
                log.warn("Retry attempt {} failed: {} (next retry in {}ms)",
                        context.getRetryCount(),
                        throwable.getMessage(),
                        waitTime);
            }
            
            private long calculateBackoffTime(int retryCount) {
                long backoff = (long) (INITIAL_INTERVAL * Math.pow(MULTIPLIER, retryCount - 1));
                return Math.min(backoff, MAX_INTERVAL);
            }
        });

        return retryTemplate;
    }
}
