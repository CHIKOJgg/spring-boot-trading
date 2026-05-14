package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
    //  PRIMARY ObjectMapper — used by Spring MVC for REST I/O
    //
    //  ROOT CAUSE OF ALL "undefined" FIELDS AND BigDecimal ERRORS:
    //  The previous @Primary ObjectMapper had activateDefaultTyping(NON_FINAL)
    //  enabled. Spring MVC auto-wires the @Primary ObjectMapper for serializing
    //  REST responses and deserializing request bodies.
    //
    //  With NON_FINAL typing, every non-final class (including response DTOs,
    //  Collections, LocalDateTime) gets wrapped in a JSON array:
    //    ["org.example.dto.response.ApiResponse$InstrumentResponse", {...}]
    //  instead of plain:
    //    {"id": 1, "ticker": "SBER", ...}
    //
    //  The frontend JavaScript received these arrays and could not parse fields,
    //  so every field rendered as "undefined".
    //
    //  For BigDecimal: when the typed mapper is used to deserialize request
    //  bodies, it expects the client to send ["java.math.BigDecimal", 100]
    //  instead of just 100, causing all deposit / fund operations to throw
    //  a 4xx deserialization error.
    //
    //  FIX: The @Primary (Spring MVC) ObjectMapper must have NO type info.
    //  A SEPARATE private mapper with type info is used exclusively inside
    //  the Redis serializer — it is never registered as a Spring bean.
    // -------------------------------------------------------

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // NO activateDefaultTyping here — Spring MVC uses this for REST JSON
        return mapper;
    }

    // -------------------------------------------------------
    //  Private Redis-only ObjectMapper (NOT a Spring bean)
    //  Type info is needed here so Redis can reconstruct the
    //  correct concrete class on cache reads.  This mapper is
    //  never injected into Spring MVC or Jackson's HTTP converters.
    // -------------------------------------------------------

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    // -------------------------------------------------------
    //  RedisTemplate  (for manual opsForValue / opsForHash calls)
    // -------------------------------------------------------

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    // -------------------------------------------------------
    //  RedisCacheManager  (used by @Cacheable / @CacheEvict)
    // -------------------------------------------------------

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper(), Object.class);

        RedisCacheConfiguration config = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(factory)
                .cacheDefaults(config)
                .build();
    }

    // -------------------------------------------------------
    //  Primary TaskExecutor for @Async
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
    //  Matching Engine singleton
    // -------------------------------------------------------

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
