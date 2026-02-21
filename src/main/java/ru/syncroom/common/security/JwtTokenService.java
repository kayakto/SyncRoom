package ru.syncroom.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.syncroom.common.config.JwtProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for generating and validating JWT tokens.
 * Uses HMAC-SHA256 (HS256) algorithm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    /**
     * Generates an access token for a user.
     * 
     * @param userId User ID
     * @param name User name
     * @param email User email (can be null)
     * @param provider Authentication provider
     * @return JWT access token
     */
    public String generateAccessToken(UUID userId, String name, String email, String provider) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("name", name);
        claims.put("email", email);
        claims.put("provider", provider);
        claims.put("type", "access");
        
        return createToken(claims, userId.toString(), jwtProperties.getAccessTokenExpiration());
    }

    /**
     * Generates a refresh token for a user.
     * 
     * @param userId User ID
     * @return JWT refresh token
     */
    public String generateRefreshToken(UUID userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId.toString());
        claims.put("type", "refresh");
        
        return createToken(claims, userId.toString(), jwtProperties.getRefreshTokenExpiration());
    }

    /**
     * Validates a JWT token and extracts the user ID.
     * 
     * @param token JWT token
     * @return User ID if token is valid
     * @throws io.jsonwebtoken.JwtException if token is invalid
     */
    public UUID validateTokenAndGetUserId(String token) {
        Claims claims = extractAllClaims(token);
        String userIdStr = claims.get("userId", String.class);
        return UUID.fromString(userIdStr);
    }

    /**
     * Validates a token and checks if it's a refresh token.
     * 
     * @param token JWT token
     * @return true if token is a valid refresh token
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);
            return "refresh".equals(type);
        } catch (Exception e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts all claims from a token.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Creates a JWT token with the given claims and expiration.
     */
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Gets the signing key from the secret.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
