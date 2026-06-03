package com.example.logistics.demo.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.demo.common.Result;
import com.example.logistics.demo.dto.LoginRequest;
import com.example.logistics.demo.util.JwtUtil;

import com.example.logistics.demo.aspect.LogExecution;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
@Tag(name = "用户认证", description = "登录获取 JWT Token")
public class AuthController {

    private static final String DEMO_USERNAME = "admin";
    private static final String DEMO_PASSWORD = "123456";

    @Operation(summary = "用户登录", description = "用户名密码验证通过后返回 JWT Token，后续请求需在 Authorization 头携带 Bearer Token")
    @PostMapping("/login")
    @LogExecution
    public Result<Map<String, String>> login(@RequestBody @Valid LoginRequest request) {
        if (!DEMO_USERNAME.equals(request.getUsername())
                || !DEMO_PASSWORD.equals(request.getPassword())) {
            return Result.fail("用户名或密码错误");
        }

        String token = JwtUtil.generateToken(request.getUsername());
        return Result.success(Map.of("token", token));
    }
}
