package org.example.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.domain.service.MatchingEngine;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
// BUG FIX #5: @EnableCaching was missing. MarketDataService.getActiveInstruments()
// uses @Cacheable("instruments") but Spring cache was never activated —
// the annotation was silently ignored and every call hit the database.
@EnableCaching
// BUG FIX #6: @EnableAsync was missing. AuditService and NotificationService
// declare @Async methods, but without this annotation they ran synchronously
// on the request thread, adding DB latency to every API response.
@EnableAsync
public class AppConfig {

    // -------------------------------------------------------
    //  Redis
    // -------------------------------------------------------

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    // -------------------------------------------------------
    //  Matching Engine — singleton bean
    // -------------------------------------------------------

    @Bean
    public MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }
}
