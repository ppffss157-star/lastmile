package com.example.order.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.service.OrderService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public Order create(
            @RequestParam(defaultValue = "张三") String customerName,
            @RequestParam(defaultValue = "北京") String address,
            @RequestParam(defaultValue = "13800138000") String phone) {
        return orderService.create(customerName, address, phone);
    }

    @GetMapping
    public List<Order> findAll() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public Order findById(@PathVariable Long id) {
        return orderService.findById(id);
    }

    /** 派单：order-service → HTTP → courier-service */
    @PutMapping("/{id}/assign")
    public Order assignCourier(@PathVariable Long id, @RequestParam Long courierId) {
        return orderService.assignCourier(id, courierId);
    }

    /** 状态流转 */
    @PutMapping("/{id}/status")
    public Order updateStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        return orderService.updateStatus(id, status);
    }
}
