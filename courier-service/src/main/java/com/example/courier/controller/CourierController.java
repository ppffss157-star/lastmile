package com.example.courier.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.courier.entity.Courier;
import com.example.courier.service.CourierService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/couriers")
@RequiredArgsConstructor
public class CourierController {

    private final CourierService courierService;

    @PostMapping
    public Courier create(
            @RequestParam(defaultValue = "快递员小王") String name,
            @RequestParam(defaultValue = "13900001111") String phone) {
        return courierService.create(name, phone);
    }

    @GetMapping
    public List<Courier> findAll() {
        return courierService.findAll();
    }

    @GetMapping("/available")
    public List<Courier> findAvailable() {
        return courierService.findAvailable();
    }

    @GetMapping("/{id}")
    public Courier findById(@PathVariable Long id) {
        return courierService.findById(id);
    }

    /** 故障开关：true = assign 接口会抛异常，用于演示 Resilience4j 熔断 */
    private volatile boolean chaosMode = false;

    /** 切换故障开关 */
    @PostMapping("/chaos/fail")
    public String toggleChaos(@RequestParam(defaultValue = "true") boolean enable) {
        this.chaosMode = enable;
        return "chaosMode=" + chaosMode + "（assign 接口现在" + (chaosMode ? "会💥爆炸" : "正常") + "）";
    }

    /**
     * 派单接口 — 被 order-service 通过 HTTP 调用。
     * 返回纯文本方便 order-service 解析。
     */
    @PostMapping("/{id}/assign")
    public String assignToOrder(@PathVariable Long id, @RequestParam Long orderId) {
        if (chaosMode) {
            throw new RuntimeException("🎯 模拟故障: courier-service 炸了！");
        }
        courierService.assignToOrder(id, orderId);
        return "配送员 " + id + " 已分配给订单 " + orderId;
    }

    @PutMapping("/{id}/release")
    public Courier release(@PathVariable Long id) {
        return courierService.release(id);
    }
}
