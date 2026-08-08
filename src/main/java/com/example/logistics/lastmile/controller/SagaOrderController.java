package com.example.logistics.lastmile.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.entity.SagaStep;
import com.example.logistics.lastmile.service.InventoryService;
import com.example.logistics.lastmile.service.PaymentService;
import com.example.logistics.lastmile.service.SagaOrchestrator;
import com.example.logistics.lastmile.service.SagaOrchestrator.SagaResult;

import lombok.RequiredArgsConstructor;

/**
 * Saga 分布式事务演示接口。
 *
 * 使用方式：
 * 1. 正常下单：POST /api/saga/order?customerName=张三&address=北京&phone=13800138000
 * 2. 触发补偿（库存故障）：先调 /api/saga/force-fail/inventory，再下单
 * 3. 触发补偿（支付故障）：先调 /api/saga/force-fail/payment，再下单
 * 4. 查看 Saga 步骤：GET /api/saga/steps/{sagaId}
 * 5. 查看模拟状态：GET /api/saga/status
 */
@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
public class SagaOrderController {

    private final SagaOrchestrator sagaOrchestrator;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    /**
     * 通过 Saga 流程下单。
     * 正常路径：PENDING → 扣库存 → 扣款 → CREATED
     * 故障路径：PENDING → 扣库存(❌) → 补偿 CREATE_ORDER → CANCELLED
     */
    @PostMapping("/order")
    public SagaResult createOrder(
            @RequestParam(defaultValue = "张三") String customerName,
            @RequestParam(defaultValue = "北京市朝阳区") String address,
            @RequestParam(defaultValue = "13800138000") String phone,
            @RequestParam(defaultValue = "PROD-001") String productId,
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(defaultValue = "USER-001") String accountId,
            @RequestParam(defaultValue = "99.00") BigDecimal amount) {

        return sagaOrchestrator.startCreateOrderSaga(
                customerName, address, phone,
                productId, quantity,
                accountId, amount);
    }

    /**
     * 注入故障：让下次扣库存或扣款失败，触发补偿流程。
     *
     * @param service inventory 或 payment
     */
    @PostMapping("/force-fail/{service}")
    public Map<String, String> forceFail(@PathVariable String service) {
        return switch (service.toLowerCase()) {
            case "inventory" -> {
                inventoryService.forceFailNext();
                yield Map.of("status", "ok", "message", "下次扣库存将失败");
            }
            case "payment" -> {
                paymentService.forceFailNext();
                yield Map.of("status", "ok", "message", "下次扣款将失败");
            }
            default -> Map.of("status", "error", "message", "只支持 inventory 或 payment");
        };
    }

    /**
     * 查询一次 Saga 事务的所有步骤。
     */
    @GetMapping("/steps/{sagaId}")
    public List<SagaStep> getSteps(@PathVariable String sagaId) {
        return sagaOrchestrator.getSteps(sagaId);
    }

    /**
     * 查看模拟系统的状态（库存、余额）。
     * 对比下单前后的数值，观察补偿是否生效。
     */
    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return Map.of(
                "stock", Map.of(
                        "PROD-001", inventoryService.getStock("PROD-001"),
                        "PROD-002", inventoryService.getStock("PROD-002")
                ),
                "accounts", Map.of(
                        "USER-001", paymentService.getBalance("USER-001"),
                        "USER-002", paymentService.getBalance("USER-002")
                )
        );
    }
}
