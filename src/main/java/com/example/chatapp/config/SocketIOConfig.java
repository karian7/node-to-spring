package com.example.chatapp.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.corundumstudio.socketio.store.RedissonStoreFactory;
import com.example.chatapp.websocket.socketio.AuthTokenListenerImpl;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIOConfig {

    private final AuthTokenListenerImpl authTokenListenerImpl;

    @Value("${socketio.server.host:localhost}")
    private String host;

    @Value("${socketio.server.port:5002}")
    private Integer port;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer();

        singleServerConfig.setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());
        singleServerConfig.setDatabase(redisProperties.getDatabase());

        return Redisson.create(config);
    }

    @Bean
    public SocketIOServer socketIOServer(RedissonClient redissonClient) {
        Configuration config = new Configuration();
        config.setHostname(host);
        config.setPort(port);

        String allowedOrigins = String.join(",",
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
        config.setOrigin(allowedOrigins);

        // Socket.IO settings
        config.setPingTimeout(60000);
        config.setPingInterval(25000);
        config.setUpgradeTimeout(10000);

        config.setJsonSupport(new JacksonJsonSupport(new JavaTimeModule()));
        config.setStoreFactory(new RedissonStoreFactory(redissonClient));

        log.info("Socket.IO server configured on {}:{} with CORS origins: {}", host, port, allowedOrigins);
        var socketIOServer = new SocketIOServer(config);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(authTokenListenerImpl);
        
        return socketIOServer;
    }
    
    /**
     * SpringAnnotationScanner를 사용하여 @OnEvent 어노테이션이 붙은 핸들러들을 자동으로 스캔하고 등록합니다.
     * Spring의 Component Scan 기능을 활용하여 모든 핸들러를 자동으로 찾아 등록합니다.
     */
    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }

    @Component
    @RequiredArgsConstructor
    @Slf4j
    public static class SocketIOServerRunner implements CommandLineRunner, DisposableBean {

        private final SocketIOServer socketIOServer;

        @Override
        public void run(String... args) {
            socketIOServer.start();
            log.info("Socket.IO server started successfully on port {}",
                    socketIOServer.getConfiguration().getPort());
        }

        @Override
        public void destroy() {
            if (socketIOServer != null) {
                socketIOServer.stop();
                log.info("Socket.IO server stopped");
            }
        }
    }

}
