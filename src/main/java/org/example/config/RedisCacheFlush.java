package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Flushes all Redis cache keys on startup.
 *
 * WHY THIS EXISTS:
 * A previous deployment stored cache entries using an ObjectMapper that had
 * activateDefaultTyping(NON_FINAL) enabled, wrapping every value in a JSON
 * array like ["org.example.dto...", {...}].
 * The corrected ObjectMapper still uses type info in Redis but the old entries
 * are structurally incompatible and cause SerializationException on every read.
 *
 * Flushing on startup forces a clean slate.  All caches warm up naturally on
 * the first request after startup.  This class can be removed once all
 * environments have been restarted at least once with the corrected config.
 */
@Component
public class RedisCacheFlush {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheFlush.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheFlush(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void flushCacheOnStartup() {
        try {
            var conn = redisTemplate.getConnectionFactory();
            if (conn != null) {
                conn.getConnection().serverCommands().flushDb();
                log.info("Redis cache flushed on startup — stale type-wrapped entries cleared");
            }
        } catch (Exception ex) {
            log.warn("Could not flush Redis cache on startup: {}", ex.getMessage());
        }
    }
}
