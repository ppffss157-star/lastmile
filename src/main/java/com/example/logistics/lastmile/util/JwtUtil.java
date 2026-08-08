package com.example.logistics.lastmile.util;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private final SecretKey key;

    // Access Token：短命，调接口用，15 分钟过期
    private static final long ACCESS_EXPIRATION_MS = 15 * 60 * 1000;

    // Refresh Token：长命，只用来换新的 Access Token，7 天过期
    private static final long REFRESH_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    // ==================== Access Token ====================

    public String generateAccessToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    // ==================== Refresh Token ====================

    /**
     * 生成 Refresh Token，内含唯一 tokenId（UUID），用于数据库关联和吊销
     */
    public String generateRefreshToken(String username) {
        String tokenId = UUID.randomUUID().toString();
        return Jwts.builder()
                .subject(username)
                .claim("tokenId", tokenId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_EXPIRATION_MS))
                .signWith(key)
                .compact();
    }

    public String extractTokenId(String token) {
        return parseClaims(token).get("tokenId", String.class);
    }

    // ==================== 通用方法 ====================

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public String extractTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 验证 token 是否是指定类型的有效 token
     */
    public boolean isTokenOfType(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            return expectedType.equals(claims.get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 兼容旧调用（其他模块可能还在用） ====================

    public String generateToken(String username, String role) {
        return generateAccessToken(username, role);
    }

    public String generateToken(String username) {
        return generateAccessToken(username, "USER");
    }

    // ==================== 内部方法 ====================

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
