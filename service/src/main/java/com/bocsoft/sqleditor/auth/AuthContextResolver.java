package com.bocsoft.sqleditor.auth;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class AuthContextResolver {
    private final AuthClient client;
    private final Clock clock;
    private final int ttlSeconds;
    private final Cache<String, AuthContext> cache;
    private final SqlEditorMetrics metrics;

    public AuthContextResolver(AuthClient client, Clock clock, SqlEditorProperties properties, SqlEditorMetrics metrics) {
        this.client = client;
        this.clock = clock;
        this.ttlSeconds = properties.getAuth().getCacheTtlSeconds();
        this.metrics = metrics;
        this.cache = Caffeine.newBuilder()
            .maximumSize(properties.getAuth().getCacheMaximumSize())
            .expireAfterWrite(Math.max(1, ttlSeconds), TimeUnit.SECONDS)
            .build();
    }

    public AuthContext resolve(String token) {
        String digest = digest(token);
        if (ttlSeconds > 0) {
            AuthContext cached = cache.getIfPresent(digest);
            if (cached != null) {
                if (cached.getTokenExpiresAt().isAfter(clock.instant())) { metrics.auth("success"); return cached; }
                cache.invalidate(digest);
                throw ApiException.unauthenticated();
            }
        }
        AuthContext resolved;
        try { resolved = client.resolve(token); metrics.auth("success"); }
        catch (ApiException exception) { metrics.auth("AUTH_SERVICE_UNAVAILABLE".equals(exception.getCode())?"unavailable":"invalid"); throw exception; }
        if (ttlSeconds > 0) cache.put(digest, resolved);
        return resolved;
    }

    private String digest(String token) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
