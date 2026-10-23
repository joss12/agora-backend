package com.springcommerce.backend.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
public class PasswordResetRateLimiter {

    private static final long WINDOW_SECONDS = 900;

    private static final String SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            local ttl = redis.call('TTL', KEYS[1])
            return {count, ttl}
            """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<List> rateLimitScript;

    public PasswordResetRateLimiter(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;

        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(SCRIPT);
        this.rateLimitScript.setResultType(List.class);
    }

    public void checkForgotPassword(
            HttpServletRequest request
    ) {
        check(
                "forgot-password",
                request,
                5
        );
    }

    public void checkResetPassword(
            HttpServletRequest request
    ) {
        check(
                "reset-password",
                request,
                10
        );
    }

    private void check(
            String action,
            HttpServletRequest request,
            int maxRequests
    ) {
        // Uses the direct connection address.
        // Do not trust client-supplied X-Forwarded-For headers.
        String clientIp = request.getRemoteAddr();

        String key = "agora:rate-limit:"
                + action
                + ":"
                + clientIp;

        List<?> result = redisTemplate.execute(
                rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(WINDOW_SECONDS)
        );

        if (result == null || result.size() < 2) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Rate limiter unavailable"
            );
        }

        long count = ((Number) result.get(0)).longValue();

        if (count > maxRequests) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please try again later."
            );
        }
    }
}