package com.example.logistics.lastmile.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.common.Result;
import com.example.logistics.lastmile.dto.LoginRequest;
import com.example.logistics.lastmile.dto.RefreshTokenRequest;
import com.example.logistics.lastmile.entity.RefreshToken;
import com.example.logistics.lastmile.repository.RefreshTokenRepository;
import com.example.logistics.lastmile.util.JwtUtil;

import com.example.logistics.lastmile.annotation.AuditLog;
import com.example.logistics.lastmile.annotation.RateLimit;
import com.example.logistics.lastmile.aspect.LogExecution;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "用户认证", description = "登录获取 JWT Token，Refresh Token 续期")
public class AuthController {

    private static final String DEMO_USERNAME = "admin";
    // BCrypt 哈希：对 "123456" 做 2^10=1024 轮哈希 + 随机盐
    private static final String DEMO_PASSWORD_HASH =
            "$2a$10$R0szi8ITBtlIG/2UtcSDR.LdYf.IdKevL9mzxdkO2Zsu9gCF1JyqK";

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;

    public AuthController(PasswordEncoder passwordEncoder,
                          RefreshTokenRepository refreshTokenRepository,
                          JwtUtil jwtUtil) {
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtUtil = jwtUtil;
    }

    @Operation(summary = "用户登录", description = "用户名密码验证通过后返回 Access Token（15分钟）+ Refresh Token（7天）")
    @PostMapping("/login")
    @RateLimit(maxRequests = 5, windowSeconds = 60)
    @LogExecution
    @AuditLog("用户登录")
    public Result<Map<String, String>> login(@RequestBody @Valid LoginRequest request) {
        if (!DEMO_USERNAME.equals(request.getUsername())
                || !passwordEncoder.matches(request.getPassword(), DEMO_PASSWORD_HASH)) {
            return Result.fail("用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(request.getUsername(), "ADMIN");
        String refreshToken = jwtUtil.generateRefreshToken(request.getUsername());

        // 把 Refresh Token 存库，后续可以吊销
        String tokenId = jwtUtil.extractTokenId(refreshToken);
        RefreshToken entity = new RefreshToken(
                tokenId,
                request.getUsername(),
                Instant.now().plusSeconds(7 * 24 * 3600) // 7 天后过期
        );
        refreshTokenRepository.save(entity);

        return Result.success(Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken
        ));
    }

    @Operation(summary = "刷新 Token", description = "用 Refresh Token 换新的 Access Token + Refresh Token（旧 Refresh Token 立即作废）")
    @PostMapping("/refresh")
    @RateLimit(maxRequests = 10, windowSeconds = 60)
    @LogExecution
    @AuditLog("刷新Token")
    public Result<Map<String, String>> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        // 1. 校验 JWT 签名和过期
        if (!jwtUtil.isTokenValid(refreshTokenStr)) {
            return Result.fail("Refresh Token 无效或已过期，请重新登录");
        }

        // 2. 必须是 refresh 类型，防止用 access token 来刷新
        if (!"refresh".equals(jwtUtil.extractTokenType(refreshTokenStr))) {
            return Result.fail("Token 类型错误，请使用 Refresh Token");
        }

        String tokenId = jwtUtil.extractTokenId(refreshTokenStr);
        String username = jwtUtil.extractUsername(refreshTokenStr);

        // 3. 查库：这个 token 还在不在（不在 = 已经被用过 / 被吊销）
        RefreshToken stored = refreshTokenRepository.findByTokenId(tokenId).orElse(null);
        if (stored == null) {
            // token 被用过 / 被吊销 → 可能是 token 被盗！
            // 安全策略：清掉该用户所有 refresh token，强制重新登录
            refreshTokenRepository.deleteByUsername(username);
            return Result.fail("Refresh Token 已被使用，请重新登录");
        }

        // 4. 检查是否过期
        if (stored.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(stored);
            return Result.fail("Refresh Token 已过期，请重新登录");
        }

        // 5. Rotation：删旧发新，旧的立刻作废
        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtUtil.generateAccessToken(username, "ADMIN");
        String newRefreshToken = jwtUtil.generateRefreshToken(username);

        String newTokenId = jwtUtil.extractTokenId(newRefreshToken);
        RefreshToken newEntity = new RefreshToken(
                newTokenId,
                username,
                Instant.now().plusSeconds(7 * 24 * 3600)
        );
        refreshTokenRepository.save(newEntity);

        return Result.success(Map.of(
                "accessToken", newAccessToken,
                "refreshToken", newRefreshToken
        ));
    }
}
