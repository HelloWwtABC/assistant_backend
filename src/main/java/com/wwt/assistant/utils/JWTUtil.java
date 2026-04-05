package com.wwt.assistant.utils;

import com.wwt.assistant.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class JWTUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TEAM_ID = "teamId";
    private static final String CLAIM_USERNAME = "username";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final Key signingKey;

    public JWTUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] secretBytes = buildSecretBytes(jwtProperties);
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(Long userId, String username, Long teamId) {
        Assert.notNull(userId, "userId must not be null");
        Assert.hasText(username, "username must not be blank");

        Instant issuedAt = Instant.now();
        Instant expiration = issuedAt.plusSeconds(jwtProperties.getExpireSeconds());

        return Jwts.builder()
                .setSubject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_TEAM_ID, teamId)
                .claim(CLAIM_USERNAME, username)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiration))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, null);
    }

    public Claims parseToken(String token) {
        String actualToken = normalizeToken(token);
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(actualToken)
                    .getBody();
        } catch (ExpiredJwtException ex) {
            return ex.getClaims();
        }
    }

    public boolean validateToken(String token) {
        try {
            String actualToken = normalizeToken(token);
            Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(actualToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        Object userId = claims.get(CLAIM_USER_ID);
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return userId == null ? null : Long.parseLong(String.valueOf(userId));
    }

    public String getUsername(String token) {
        Claims claims = parseToken(token);
        String username = claims.get(CLAIM_USERNAME, String.class);
        return StringUtils.hasText(username) ? username : claims.getSubject();
    }

    public Long getTeamId(String token) {
        Claims claims = parseToken(token);
        Object teamId = claims.get(CLAIM_TEAM_ID);
        if (teamId instanceof Number number) {
            return number.longValue();
        }
        return teamId == null ? null : Long.parseLong(String.valueOf(teamId));
    }

    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    public boolean isTokenExpired(String token) {
        Date expiration = getExpiration(token);
        return expiration == null || expiration.before(new Date());
    }

    public String normalizeToken(String token) {
        Assert.hasText(token, "token must not be blank");
        String actualToken = token.trim();
        if (actualToken.startsWith(BEARER_PREFIX)) {
            actualToken = actualToken.substring(BEARER_PREFIX.length()).trim();
        }
        Assert.hasText(actualToken, "token must not be blank");
        return actualToken;
    }

    private byte[] buildSecretBytes(JwtProperties properties) {
        Assert.notNull(properties, "jwtProperties must not be null");
        Assert.hasText(properties.getSecret(), "jwt.secret must not be blank");
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        Assert.isTrue(
                secretBytes.length >= MIN_SECRET_BYTES,
                "jwt.secret must be at least 32 bytes for HS256");
        return secretBytes;
    }
}
