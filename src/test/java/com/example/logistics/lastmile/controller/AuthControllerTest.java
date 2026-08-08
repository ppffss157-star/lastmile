package com.example.logistics.lastmile.controller;

import java.util.Optional;

import com.example.logistics.lastmile.dto.LoginRequest;
import com.example.logistics.lastmile.dto.RefreshTokenRequest;
import com.example.logistics.lastmile.entity.RefreshToken;
import com.example.logistics.lastmile.repository.RefreshTokenRepository;
import com.example.logistics.lastmile.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.core.StringRedisTemplate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthControllerTest.TestConfig.class)
class AuthControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        /**
         * Spring Boot 4.0 移除了 @MockBean，改用 @TestConfiguration + mock()。
         * 这个 mock 的 Repository 会被注入到 AuthController 里。
         */
        @Bean
        RefreshTokenRepository refreshTokenRepository() {
            return mock(RefreshTokenRepository.class);
        }

        /**
         * @WebMvcTest 不加载 Redis 自动配置，但 RateLimitInterceptor 需要它。
         * Mock 一个假的 StringRedisTemplate，让拦截器能正常创建。
         */
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil("test-secret-key-at-least-256-bits-long-enough-for-hs256");
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    // ==================== 登录测试 ====================

    @Test
    void shouldLoginSuccessfully() throws Exception {
        // 模拟数据库保存成功（refresh token 存库）
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void shouldFailLoginWithWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void shouldFailLoginWithEmptyUsername() throws Exception {
        String invalidJson = """
                {"username": "", "password": "123456"}
                """;

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // ==================== Refresh Token 测试 ====================

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // 先生成一个真实的 refresh token
        String refreshTokenStr = jwtUtil.generateRefreshToken("admin");
        String tokenId = jwtUtil.extractTokenId(refreshTokenStr);

        // 模拟数据库里能找到这个 token
        RefreshToken savedToken = new RefreshToken(
                tokenId, "admin",
                java.time.Instant.now().plusSeconds(7 * 24 * 3600)
        );
        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(savedToken));
        // save 直接返回参数（rotation 时会删旧存新）
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshTokenStr);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void shouldRejectRefreshWithAccessToken() throws Exception {
        // 拿 Access Token 去调 refresh → 应该被拒
        String accessToken = jwtUtil.generateAccessToken("admin", "ADMIN");

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(accessToken);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Token 类型错误，请使用 Refresh Token"));
    }

    @Test
    void shouldRejectReusedRefreshToken() throws Exception {
        // 模拟 token 被用过一次的场景（rotation 保护）
        String refreshTokenStr = jwtUtil.generateRefreshToken("admin");
        String tokenId = jwtUtil.extractTokenId(refreshTokenStr);

        // 数据库里找不到 → 说明被用过了
        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.empty());

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshTokenStr);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Refresh Token 已被使用，请重新登录"));
    }

    @Test
    void shouldRejectExpiredRefreshToken() throws Exception {
        String refreshTokenStr = jwtUtil.generateRefreshToken("admin");
        String tokenId = jwtUtil.extractTokenId(refreshTokenStr);

        // 数据库记录显示已过期
        RefreshToken expiredToken = new RefreshToken(
                tokenId, "admin",
                java.time.Instant.now().minusSeconds(3600) // 1 小时前过期
        );
        when(refreshTokenRepository.findByTokenId(tokenId))
                .thenReturn(Optional.of(expiredToken));

        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken(refreshTokenStr);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Refresh Token 已过期，请重新登录"));
    }
}
