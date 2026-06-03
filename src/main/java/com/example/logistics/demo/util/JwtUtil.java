package com.example.logistics.demo.util;

import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class JwtUtil {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "this-is-a-demo-secret-key-at-least-256-bits-long-for-hs256".getBytes());
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 小时

    public static String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(KEY)
                .compact();
    }

    public static String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public static boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
