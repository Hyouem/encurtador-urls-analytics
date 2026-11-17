package com.hyouem.shortener;

import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LinkCache {

    private static final Duration MAX_TTL = Duration.ofSeconds(60);
    private static final String PREFIX = "shortener:link:";

    private final StringRedisTemplate redis;

    public LinkCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public String get(String code) {
        try {
            return redis.opsForValue().get(PREFIX + code);
        } catch (DataAccessException exception) {
            return null;
        }
    }

    public void put(String code, String url, Instant expiresAt) {
        Duration ttl = MAX_TTL;

        if (expiresAt != null) {
            Duration remaining = Duration.between(Instant.now(), expiresAt);

            if (remaining.isNegative() || remaining.isZero()) {
                return;
            }

            if (remaining.compareTo(ttl) < 0) {
                ttl = remaining;
            }
        }

        try {
            redis.opsForValue().set(PREFIX + code, url, ttl);
        } catch (DataAccessException exception) {
            // Redis indisponivel nao impede persistencia de cliques.
        }
    }

    public void invalidate(String code) {
        try {
            redis.delete(PREFIX + code);
        } catch (DataAccessException exception) {
            // A verificacao no PostgreSQL continua obrigatoria.
        }
    }
}