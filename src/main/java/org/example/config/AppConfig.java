package org.example.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.domain.service.MatchingEngine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableCaching
@EnableAsync
public class AppConfig {

    // -------------------------------------------------------
    //  Jackson ObjectMapper shared by all Redis serializers
    // -------------------------------------------------------

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Store class type info so polymorphic deserialization works
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    // -------------------------------------------------------
    //  RedisTemplate  (for manual RedisTemplate.opsForValue calls)
    // -------------------------------------------------------

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper(), Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    // -------------------------------------------------------
    //  RedisCacheManager — CRITICAL FIX
    //
    //  Spring Boot's auto-configured RedisCacheManager uses
    //  JdkSerializationRedisSerializer by default.  DTOs like
    //  InstrumentResponse do NOT implement Serializable, so every
    //  @Cacheable write threw:
    //    NotSerializableException: ApiResponse$InstrumentResponse
    //  and the /api/market/instruments endpoint returned 500.
    //
    //  The fix is to define an explicit RedisCacheManager that uses
    //  the Jackson2JsonRedisSerializer instead.  Spring Boot backs
    //  off its auto-config when we provide our own bean.
    // -------------------------------------------------------

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper(), Object.class);

        RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(serializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    // -------------------------------------------------------
    //  Primary TaskExecutor for @Async
    //
    //  WebSocket creates three executor beans named
    //  clientInboundChannelExecutor, clientOutboundChannelExecutor,
    //  and brokerChannelExecutor.  Without a bean named "taskExecutor"
    //  Spring's @Async infrastructure can't pick one and logs:
    //    "More than one TaskExecutor bean found within the context,
    //     and none is named 'taskExecutor'."
    //  As a result @Async methods fall back to synchronous execution.
    // -------------------------------------------------------

    @Bean(name = "taskExecutor")
    @Primary
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-exec-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    // -------------------------------------------------------
    //  Matching Engine — singleton bean
    // -------------------------------------------------------

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
