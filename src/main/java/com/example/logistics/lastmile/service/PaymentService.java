package com.example.logistics.lastmile.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 模拟支付服务（在真实微服务架构中这是一个独立服务）。
 *
 * 每个操作都有对应的补偿操作：
 *   processPayment()  ←→  refundPayment()
 *
 * 模拟要素：
 *   - ConcurrentHashMap 充当"账户余额表"
 *   - Thread.sleep 模拟网络延迟
 *   - 可注入故障模拟（forceFail）
 *   - 余额不足时返回 false（业务失败，不是系统异常）
 */
@Slf4j
@Service
public class PaymentService {

    /** 模拟账户余额：accountId → 余额 */
    private final Map<String, BigDecimal> accounts = new ConcurrentHashMap<>();

    /** 强制下次扣款抛异常 */
    private volatile boolean forceFail = false;

    public PaymentService() {
        accounts.put("USER-001", new BigDecimal("10000.00"));
        accounts.put("USER-002", new BigDecimal("500.00"));
    }

    /**
     * 扣款。
     * @return true 扣款成功，false 余额不足
     */
    public boolean processPayment(Long orderId, String accountId, BigDecimal amount) {
        simulateNetworkDelay("扣款");

        if (forceFail) {
            forceFail = false;
            log.error("[Saga-支付] 模拟故障：扣款失败 orderId={}", orderId);
            throw new RuntimeException("支付服务不可用（模拟故障）");
        }

        BigDecimal balance = accounts.getOrDefault(accountId, BigDecimal.ZERO);
        if (balance.compareTo(amount) < 0) {
            log.warn("[Saga-支付] 余额不足 orderId={} accountId={} 余额={} 需支付={}",
                    orderId, accountId, balance, amount);
            return false;
        }

        accounts.put(accountId, balance.subtract(amount));
        log.info("[Saga-支付] ✅ 扣款成功 orderId={} accountId={} 扣款={} 余额={}",
                orderId, accountId, amount, accounts.get(accountId));
        return true;
    }

    /**
     * 补偿：退款（对应 processPayment 的补偿操作）。
     * 幂等：实际项目中应通过 sagaId + orderId 判断是否已退款。
     */
    public void refundPayment(Long orderId, String accountId, BigDecimal amount) {
        simulateNetworkDelay("退款(补偿)");
        accounts.merge(accountId, amount, BigDecimal::add);
        log.info("[Saga-补偿] 🔄 已退款 orderId={} accountId={} 退款={} 余额={}",
                orderId, accountId, amount, accounts.get(accountId));
    }

    public BigDecimal getBalance(String accountId) {
        return accounts.getOrDefault(accountId, BigDecimal.ZERO);
    }

    /** 注入故障：下次扣款抛异常 */
    public void forceFailNext() {
        this.forceFail = true;
        log.warn("[Saga-支付] ⚠️ 已设置下次扣款强制失败");
    }

    public void setBalance(String accountId, BigDecimal amount) {
        accounts.put(accountId, amount);
    }

    private void simulateNetworkDelay(String operation) {
        try {
            Thread.sleep(200 + (long) (Math.random() * 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("[Saga-支付] {} 完成（模拟网络延迟）", operation);
    }
}
