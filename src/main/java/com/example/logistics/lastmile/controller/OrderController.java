package com.example.logistics.lastmile.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.annotation.AuditLog;
import com.example.logistics.lastmile.annotation.RateLimit;
import com.example.logistics.lastmile.common.Result;
import com.example.logistics.lastmile.dto.CreateOrderRequest;
import com.example.logistics.lastmile.dto.UpdateOrderStatusRequest;
import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.service.OrderService;

import com.example.logistics.lastmile.aspect.LogExecution;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/orders")
@RateLimit(maxRequests = 30, windowSeconds = 60)
@Tag(name = "订单管理", description = "订单的创建、查询、状态流转、取消、删除")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "创建订单", description = "客户填写姓名、地址、手机号下单，订单状态初始为 CREATED")
    @PostMapping
    @LogExecution
    @AuditLog("创建订单")
    public Result<Order> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        return Result.success(orderService.create(request));
    }

    @Operation(summary = "查询全部订单", description = "返回所有订单列表")
    @GetMapping
    @LogExecution
    public Result<List<Order>> getAllOrders() {
        return Result.success(orderService.findAll());
    }

    @Operation(summary = "分页查询订单", description = "按页码和每页条数分页返回订单数据")
    @GetMapping("/page")
    @LogExecution
    public Result<Page<Order>> getOrdersByPage(
            @Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "5") int size) {
        return Result.success(orderService.findPage(page, size));
    }

    @Operation(summary = "按配送员查询订单（分页）", description = "分页查看某个配送员名下的订单")
    @GetMapping("/courier/{courierId}")
    @LogExecution
    public Result<Page<Order>> getOrdersByCourierId(
            @Parameter(description = "配送员 ID") @PathVariable Long courierId,
            @Parameter(description = "页码，从 0 开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "5") int size) {
        return Result.success(orderService.findByCourierId(courierId, PageRequest.of(page, size)));
    }

    @Operation(summary = "按 ID 查订单", description = "根据订单 ID 查询单个订单详情")
    @GetMapping("/{id}")
    @LogExecution
    public Result<Order> getOrderById(
            @Parameter(description = "订单 ID") @PathVariable Long id) {
        Order order = orderService.findById(id);
        return Result.success(order);
    }

    @Operation(summary = "更新订单状态", description = "修改订单状态，系统会校验状态流转是否合法")
    @PutMapping("/{id}/status")
    @LogExecution
    @AuditLog("更新订单状态")
    public Result<Order> updateStatus(
            @Parameter(description = "订单 ID") @PathVariable Long id,
            @RequestBody @Valid UpdateOrderStatusRequest request) {
        Order order = orderService.updateStatus(id, request.getStatus());
        return Result.success(order);
    }

    @Operation(summary = "派单", description = "将订单分配给指定配送员，使用 Redis 分布式锁防止重复派单")
    @PutMapping("/{orderId}/assign/{courierId}")
    @LogExecution
    @AuditLog("派单")
    public Result<Order> assignCourier(
            @Parameter(description = "订单 ID") @PathVariable Long orderId,
            @Parameter(description = "配送员 ID") @PathVariable Long courierId) {
        Order order = orderService.assignCourier(orderId, courierId);
        return Result.success(order);
    }

    @Operation(summary = "取消订单", description = "将订单状态改为 CANCELLED，仅 CREATED 状态的订单可取消")
    @PutMapping("/{id}/cancel")
    @LogExecution
    @AuditLog("取消订单")
    public Result<Order> cancelOrder(
            @Parameter(description = "订单 ID") @PathVariable Long id) {
        Order order = orderService.cancelOrder(id);
        return Result.success(order);
    }

    @Operation(summary = "订单统计", description = "按状态分组统计订单数量")
    @GetMapping("/stats")
    @LogExecution
    public Result<Map<OrderStatus, Long>> getOrderStats() {
        return Result.success(orderService.getOrderStats());
    }

    @Operation(summary = "客户名单", description = "获取所有不重复的客户名（去重+排序）")
    @GetMapping("/customers")
    @LogExecution
    public Result<List<String>> getCustomerNames() {
        return Result.success(orderService.findAllCustomerNames());
    }

    // ES 搜索端点已临时移除（dev 环境无 ES），恢复时取消注释并恢复 OrderSearchService
    // @Operation(summary = "全文搜索订单")
    // @GetMapping("/search")
    // public Result<?> searchOrders(...) { ... }

    @Operation(summary = "删除订单", description = "根据 ID 删除订单")
    @DeleteMapping("/{id}")
    @LogExecution
    @AuditLog("删除订单")
    public Result<String> deleteOrder(
            @Parameter(description = "订单 ID") @PathVariable Long id) {
        orderService.deleteById(id);
        return Result.success("删除成功");
    }
}