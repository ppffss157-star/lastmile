package com.example.logistics.lastmile.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;
import com.example.logistics.lastmile.entity.SagaStep;
import com.example.logistics.lastmile.entity.SagaStepStatus;
import com.example.logistics.lastmile.repository.OrderRepository;
import com.example.logistics.lastmile.repository.SagaStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Saga 编排器 — 分布式事务的核心。
 *
 * 职责：
 * 1. 定义步骤链（先做什么、后做什么）
 * 2. 顺序执行每一步
 * 3. 任一步失败 → 反向执行已成功步骤的补偿操作
 * 4. 记录每一步的执行结果到 saga_steps 表
 *
 * 和 TCC 的区别：
 *   - TCC 每个服务要写 Try/Confirm/Cancel 三个接口
 *   - Saga 每个服务只需要 正操作 + 补偿操作（补偿是一个新的业务操作，不是回滚）
 *
 * 当前 Saga 只有一条固定的下单链路。真实项目中会抽象成可配置的步骤链。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final SagaStepRepository sagaStepRepository;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    /**
     * 启动一次 Saga 事务 — 下单流程。
     *
     * @param customerName 客户名
     * @param address      地址
     * @param phone        电话
     * @param productId    商品 ID
     * @param quantity     数量
     * @param accountId    支付账户
     * @param amount       金额
     * @return SagaResult 包含 sagaId、订单、是否全部成功、每步状态
     */
    @Transactional(rollbackFor = Exception.class)
    public SagaResult startCreateOrderSaga(
            String customerName, String address, String phone,
            String productId, int quantity,
            String accountId, BigDecimal amount) {

        String sagaId = UUID.randomUUID().toString().substring(0, 8);
        log.info("═══════════════════════════════════════════");
        log.info("[Saga-{}] 🚀 开始下单流程", sagaId);
        log.info("[Saga-{}] 客户={} 商品={} 数量={} 金额={}", sagaId, customerName, productId, quantity, amount);
        log.info("═══════════════════════════════════════════");

        // ===== Step 1: 创建订单 =====
        Order order = executeStep(sagaId, null, "CREATE_ORDER", null, () -> {
            Order o = new Order();
            o.setCustomerName(customerName);
            o.setAddress(address);
            o.setPhone(phone);
            o.setStatus(OrderStatus.PENDING);
            o.setCreatedAt(LocalDateTime.now());
            Order saved = orderRepository.save(o);
            log.info("[Saga-{}] 📝 Step1-CREATE_ORDER: 订单已创建(PENDING) orderId={}",
                    sagaId, saved.getId());
            return saved;
        });

        if (order == null) {
            return failResult(sagaId, null);
        }

        Long orderId = order.getId();

        // ===== Step 2: 扣库存 =====
        Map<String, Object> inventoryCtx = new HashMap<>();
        inventoryCtx.put("productId", productId);
        inventoryCtx.put("quantity", quantity);
        Boolean inventoryOk = executeStep(sagaId, orderId, "RESERVE_INVENTORY", inventoryCtx, () -> {
            boolean success = inventoryService.reserveInventory(orderId, productId, quantity);
            if (!success) {
                throw new RuntimeException("库存不足: " + productId + " 需要 " + quantity);
            }
            return success;
        });

        if (inventoryOk == null || !inventoryOk) {
            compensate(sagaId, orderId);
            return failResult(sagaId, orderId);
        }

        // ===== Step 3: 扣款 =====
        Map<String, Object> paymentCtx = new HashMap<>();
        paymentCtx.put("accountId", accountId);
        paymentCtx.put("amount", amount.toString());
        Boolean paymentOk = executeStep(sagaId, orderId, "PROCESS_PAYMENT", paymentCtx, () -> {
            boolean success = paymentService.processPayment(orderId, accountId, amount);
            if (!success) {
                throw new RuntimeException("余额不足: " + accountId + " 需支付 " + amount);
            }
            return success;
        });

        if (paymentOk == null || !paymentOk) {
            compensate(sagaId, orderId);
            return failResult(sagaId, orderId);
        }

        // ===== 全部成功：确认订单 =====
        order.setStatus(OrderStatus.CREATED);
        orderRepository.save(order);
        log.info("═══════════════════════════════════════════");
        log.info("[Saga-{}] 🎉 下单流程全部完成！ orderId={}", sagaId, orderId);
        log.info("═══════════════════════════════════════════");

        List<SagaStep> steps = sagaStepRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
        return SagaResult.success(sagaId, orderId, steps);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行单个步骤：记录 PENDING → 执行 → 更新 SUCCESS 或 FAILED。
     *
     * @param sagaId   Saga 事务 ID
     * @param orderId  订单 ID（第一步时可能为 null）
     * @param stepName 步骤名称
     * @param action   要执行的操作
     * @return 操作返回值，失败返回 null
     */
    private <T> T executeStep(String sagaId, Long orderId, String stepName,
                               Map<String, Object> context,
                               java.util.function.Supplier<T> action) {
        // 1. 先记录步骤（PENDING），context 存 JSON 便于补偿时还原参数
        String contextJson = context != null ? toJson(context) : null;
        SagaStep step = SagaStep.builder()
                .sagaId(sagaId)
                .orderId(orderId != null ? orderId : 0L)
                .stepName(stepName)
                .status(SagaStepStatus.PENDING)
                .context(contextJson)
                .createdAt(LocalDateTime.now())
                .build();
        sagaStepRepository.save(step);

        // 2. 执行
        try {
            T result = action.get();
            // 3a. 成功
            step.setStatus(SagaStepStatus.SUCCESS);
            sagaStepRepository.save(step);
            log.info("[Saga-{}] ✅ {}=成功", sagaId, stepName);
            return result;
        } catch (Exception e) {
            // 3b. 失败 → 记下错误信息
            step.setStatus(SagaStepStatus.FAILED);
            step.setErrorMessage(e.getMessage());
            sagaStepRepository.save(step);
            log.error("[Saga-{}] ❌ {}=失败: {}", sagaId, stepName, e.getMessage());
            return null;
        }
    }

    /**
     * 补偿：反向遍历所有成功的步骤，执行补偿操作。
     *
     * 补偿顺序是反的：
     *   先执行的操作后补偿，后执行的操作先补偿。
     *   就像撤销操作一样——Ctrl+Z 总是撤销最近的操作。
     *
     * 补偿失效场景演示（day 2 优化内容）：
     *   如果补偿本身失败（网络超时、服务宕机），记录到 step.errorMessage，
     *   标记 COMPENSATION_FAILED，需要人工介入或定时任务重试。
     */
    private void compensate(String sagaId, Long orderId) {
        log.warn("═══════════════════════════════════════════");
        log.warn("[Saga-{}] ⚠️ 开始补偿流程 orderId={}", sagaId, orderId);
        log.warn("═══════════════════════════════════════════");

        // 从后往前查所有成功的步骤
        List<SagaStep> successSteps = sagaStepRepository
                .findBySagaIdAndStatusOrderByCreatedAtAsc(sagaId, SagaStepStatus.SUCCESS);

        // 反转顺序：后执行的先补偿
        Collections.reverse(successSteps);

        for (SagaStep step : successSteps) {
            try {
                compensateStep(sagaId, orderId, step);
                step.setStatus(SagaStepStatus.COMPENSATED);
                sagaStepRepository.save(step);
                log.info("[Saga-{}] 🔄 补偿完成: {}", sagaId, step.getStepName());
            } catch (Exception e) {
                // day 2 优化：补偿失败不阻断后续补偿，记录后继续
                // 场景：支付退款失败不影响库存加回的补偿执行
                log.error("[Saga-{}] 💀 补偿失败需人工介入: {} error={}", sagaId, step.getStepName(), e.getMessage());
                step.setErrorMessage("补偿失败: " + e.getMessage());
                sagaStepRepository.save(step);
            }
        }

        // 最后把订单取消
        if (orderId != null && orderId > 0) {
            orderRepository.findById(orderId).ifPresent(o -> {
                o.setStatus(OrderStatus.CANCELLED);
                orderRepository.save(o);
                log.info("[Saga-{}] 📝 订单已取消 orderId={}", sagaId, orderId);
            });
        }
    }

    /**
     * 根据步骤记录执行对应的补偿操作，参数从 step.context 中读取。
     */
    private void compensateStep(String sagaId, Long orderId, SagaStep step) {
        String stepName = step.getStepName();
        switch (stepName) {
            case "CREATE_ORDER":
                // 补偿：取消订单（只需 orderId，不需要 context）
                orderRepository.findById(orderId).ifPresent(o -> {
                    o.setStatus(OrderStatus.CANCELLED);
                    orderRepository.save(o);
                });
                log.info("[Saga-{}] 补偿-CREATE_ORDER: 订单已取消", sagaId);
                break;

            case "RESERVE_INVENTORY": {
                Map<String, Object> ctx = fromJson(step.getContext());
                String productId = (String) ctx.getOrDefault("productId", "PROD-001");
                int quantity = ((Number) ctx.getOrDefault("quantity", 1)).intValue();
                inventoryService.releaseInventory(orderId, productId, quantity);
                log.info("[Saga-{}] 补偿-RESERVE_INVENTORY: productId={} quantity={}", sagaId, productId, quantity);
                break;
            }

            case "PROCESS_PAYMENT": {
                Map<String, Object> ctx = fromJson(step.getContext());
                String accountId = (String) ctx.getOrDefault("accountId", "USER-001");
                BigDecimal amount = new BigDecimal(ctx.getOrDefault("amount", "1.00").toString());
                paymentService.refundPayment(orderId, accountId, amount);
                log.info("[Saga-{}] 补偿-PROCESS_PAYMENT: accountId={} amount={}", sagaId, accountId, amount);
                break;
            }

            default:
                log.warn("[Saga-{}] 未知步骤类型，跳过补偿: {}", sagaId, stepName);
        }
    }

    /**
     * 查询一次 Saga 事务的所有步骤（供 Controller 调用）。
     */
    @Transactional(readOnly = true)
    public List<SagaStep> getSteps(String sagaId) {
        return sagaStepRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
    }

    private SagaResult failResult(String sagaId, Long orderId) {
        List<SagaStep> steps = sagaStepRepository.findBySagaIdOrderByCreatedAtAsc(sagaId);
        return SagaResult.fail(sagaId, orderId, steps);
    }

    // ==================== JSON 工具 ====================

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Saga context 序列化失败", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Saga context 反序列化失败，使用空 Map 兜底: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    // ==================== 结果封装 ====================

    /**
     * Saga 执行结果，包含完整的步骤记录，方便排查问题。
     */
    public record SagaResult(
            String sagaId,
            Long orderId,
            boolean success,
            List<SagaStep> steps
    ) {
        public static SagaResult success(String sagaId, Long orderId, List<SagaStep> steps) {
            return new SagaResult(sagaId, orderId, true, steps);
        }

        public static SagaResult fail(String sagaId, Long orderId, List<SagaStep> steps) {
            return new SagaResult(sagaId, orderId, false, steps);
        }
    }
}
