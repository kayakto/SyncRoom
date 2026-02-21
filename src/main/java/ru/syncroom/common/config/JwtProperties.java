package ru.syncroom.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties.
 * Values are loaded from application.yml (jwt.*).
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * Secret key for signing JWT tokens (HMAC-SHA256).
     * Should be at least 32 characters long in production.
     */
    private String secret;
    
    /**
     * Access token expiration time in milliseconds.
     * Default: 15 minutes (900000 ms).
     */
    private long accessTokenExpiration = 900000;
    
    /**
     * Refresh token expiration time in milliseconds.
     * Default: 30 days (2592000000 ms).
     */
    private long refreshTokenExpiration = 2592000000L;
}
