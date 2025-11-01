package com.ktb.chatapp.config;

import com.corundumstudio.socketio.AuthTokenListener;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.namespace.Namespace;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.corundumstudio.socketio.store.RedissonStore;
import com.corundumstudio.socketio.store.RedissonStoreFactory;
import com.corundumstudio.socketio.store.Store;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;

import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOConfig {

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

    @Bean(initMethod = "start", destroyMethod = "stop")
    public SocketIOServer socketIOServer(RedissonClient redissonClient, AuthTokenListener authTokenListener) {
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
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
        config.setStoreFactory(new ExpiringStoreFactory(redissonClient));

        log.info("Socket.IO server configured on {}:{} with CORS origins: {}", host, port, allowedOrigins);
        var socketIOServer = new SocketIOServer(config);
        socketIOServer.getNamespace(Namespace.DEFAULT_NAME).addAuthTokenListener(authTokenListener);
        
        return socketIOServer;
    }
    
    /**
     * SpringAnnotationScanner는 BeanPostProcessor로서
     * ApplicationContext 초기화 초기에 등록되고,
     * 내부에서 사용하는 SocketIOServer는 Lazy로 지연되어
     * 다른 Bean들의 초기화 과정에 간섭하지 않게 한다.
     */
    @Bean
    @Role(ROLE_INFRASTRUCTURE)
    public BeanPostProcessor springAnnotationScanner(@Lazy SocketIOServer socketIOServer) {
        return new SpringAnnotationScanner(socketIOServer);
    }
    
    static class ExpiringStoreFactory extends RedissonStoreFactory {
        private final RedissonClient redissonClient;
        
        public ExpiringStoreFactory(RedissonClient client) {
            this.redissonClient = client;
        }
        
        @Override
        public Store createStore(UUID sessionId) {
            var redissonStore = new RedissonStore(sessionId, redissonClient);
            redissonClient.getMap(sessionId.toString()).expire(Duration.ofSeconds(30));
            return redissonStore;
        }
        
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <K, V> Map<K, V> createMap(String name) {
            var map = (RMap) super.createMap(name);
            map.expire(Duration.ofSeconds(30));
            return map;
        }
    }
}
