package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    //  PRIMARY ObjectMapper — used by Spring MVC for all REST I/O
    //  NO type info: clean JSON arrays and objects for the browser.
    // -------------------------------------------------------

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    // -------------------------------------------------------
    //  RedisTemplate — for manual opsForValue / pub-sub calls
    //  Uses GenericJackson2JsonRedisSerializer which adds @class
    //  type info only for polymorphic types that need it.
    // -------------------------------------------------------

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        GenericJackson2JsonRedisSerializer serializer = redisSerializer();
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    // -------------------------------------------------------
    //  RedisCacheManager — used by @Cacheable / @CacheEvict
    //
    //  FIX: switched from Jackson2JsonRedisSerializer<Object> with
    //  activateDefaultTyping to GenericJackson2JsonRedisSerializer.
    //  GenericJackson2JsonRedisSerializer is designed specifically for
    //  Spring Cache: it writes @class info only where needed and reads
    //  back the correct concrete type without conflicting with the
    //  @Primary ObjectMapper used by Spring MVC.
    // -------------------------------------------------------

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer serializer = redisSerializer();

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

    /** Shared Redis value serializer — one instance, consistent format */
    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper redisMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // GenericJackson2JsonRedisSerializer activates its own safe default typing
        // internally on the provided mapper — this is the correct, designed-for-purpose API.
        return new GenericJackson2JsonRedisSerializer(redisMapper);
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
