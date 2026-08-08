package com.example.logistics.lastmile.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.annotation.AuditLog;
import com.example.logistics.lastmile.annotation.RateLimit;
import com.example.logistics.lastmile.common.Result;
import com.example.logistics.lastmile.dto.CreateCourierRequest;
import com.example.logistics.lastmile.dto.UpdateCourierRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.service.CourierService;

import com.example.logistics.lastmile.aspect.LogExecution;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/couriers")
@RateLimit(maxRequests = 20, windowSeconds = 60)
@Tag(name = "配送员管理", description = "配送员的注册、查询、修改、删除")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @Operation(summary = "注册配送员", description = "填写姓名和手机号注册新配送员，状态初始为 AVAILABLE")
    @PostMapping
    @LogExecution
    @AuditLog("注册配送员")
    public Result<Courier> createCourier(@RequestBody @Valid CreateCourierRequest request) {
        return Result.success(courierService.create(request));
    }

    @Operation(summary = "查询全部配送员", description = "返回所有配送员列表")
    @GetMapping
    @LogExecution
    public Result<List<Courier>> getAllCouriers() {
        return Result.success(courierService.findAll());
    }

    @Operation(summary = "按 ID 查配送员", description = "根据配送员 ID 查询详情")
    @GetMapping("/{id}")
    @LogExecution
    public Result<Courier> getCourierById(
            @Parameter(description = "配送员 ID") @PathVariable Long id) {
        Courier courier = courierService.findById(id);
        return Result.success(courier);
    }

    @Operation(summary = "修改配送员信息", description = "更新配送员的姓名、手机号或状态")
    @PutMapping("/{id}")
    @LogExecution
    @AuditLog("修改配送员信息")
    public Result<Courier> updateCourier(
            @Parameter(description = "配送员 ID") @PathVariable Long id,
            @RequestBody UpdateCourierRequest request) {
        Courier updatedCourier = courierService.update(id, request);
        return Result.success(updatedCourier);
    }

    @Operation(summary = "删除配送员", description = "根据 ID 删除配送员")
    @DeleteMapping("/{id}")
    @LogExecution
    @AuditLog("删除配送员")
    public Result<String> deleteCourier(
            @Parameter(description = "配送员 ID") @PathVariable Long id) {
        courierService.deleteById(id);
        return Result.success("删除成功");
    }
}