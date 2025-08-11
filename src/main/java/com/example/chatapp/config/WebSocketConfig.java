package com.example.chatapp.config;

import com.example.chatapp.websocket.interceptor.WebSocketAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 브로커 경로 설정 - 클라이언트가 구독할 수 있는 경로들
        config.enableSimpleBroker("/topic", "/queue");

        // 클라이언트에서 서버로 메시지를 보낼 때 사용할 prefix
        config.setApplicationDestinationPrefixes("/app");

        // 사용자별 개인 메시지를 위한 prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setHeartbeatTime(25000) // 25초마다 heartbeat
                .setDisconnectDelay(30000); // 30초 후 연결 해제

        // SockJS 없는 네이티브 WebSocket 엔드포인트도 제공
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketAuthInterceptor);
        // 동시 처리 가능한 메시지 수 설정
        registration.taskExecutor().corePoolSize(10).maxPoolSize(50);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 아웃바운드 채널 설정
        registration.taskExecutor().corePoolSize(10).maxPoolSize(50);
    }
}
