package org.samsung.api.DeviceControl.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Claims> extractClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Optional.of(claims);
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<String> extractUserId(String token) {
        return extractClaims(token)
                .map(claims -> claims.get("userId", String.class));
    }

    public boolean isTokenValid(String token) {
        return extractClaims(token)
                .map(claims -> claims.getExpiration().after(new Date()))
                .orElse(false);
    }
}
