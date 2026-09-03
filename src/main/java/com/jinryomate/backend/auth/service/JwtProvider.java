package com.jinryomate.backend.auth.service;

import com.jinryomate.backend.auth.config.AuthProperties;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** 액세스 토큰 발급·검증과 refresh 토큰 생성. */
@Component
public class JwtProvider {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;
    private final AuthProperties properties;

    public JwtProvider(AuthProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTtl())))
                .signWith(key)
                .compact();
    }

    /** @throws ApiException 서명이 틀렸거나 만료된 경우 */
    public Long parseUserId(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "토큰이 유효하지 않습니다.");
        }
    }

    /** refresh 토큰은 의미 없는 난수다. 안에 정보를 담지 않는다. */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** DB에는 원문이 아니라 해시를 넣는다. */
    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public long accessTtlSeconds() {
        return properties.accessTtl().toSeconds();
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(properties.refreshTtl());
    }
}
