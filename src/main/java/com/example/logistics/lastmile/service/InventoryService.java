package com.example.logistics.lastmile.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 模拟库存服务（在真实微服务架构中这是一个独立服务，通过 HTTP/RPC 调用）。
 *
 * 每个操作都有对应的补偿操作：
 *   reserveInventory()  ←→  releaseInventory()
 *
 * 模拟要素：
 *   - ConcurrentHashMap 充当"库存数据库"
 *   - Thread.sleep 模拟网络延迟
 *   - 可注入故障模拟（forceFail）来触发补偿流程
 */
@Slf4j
@Service
public class InventoryService {

    /** 模拟库存数据：productId → 剩余库存 */
    private final Map<String, Integer> stock = new ConcurrentHashMap<>();

    /** 设为 true 强制下次扣库存失败，用于演示补偿流程 */
    private volatile boolean forceFail = false;

    public InventoryService() {
        // 初始化一些库存
        stock.put("PROD-001", 100);
        stock.put("PROD-002", 50);
    }

    /**
     * 扣减库存。
     * @return true 扣减成功，false 库存不足
     */
    public boolean reserveInventory(Long orderId, String productId, int quantity) {
        simulateNetworkDelay("扣库存");

        if (forceFail) {
            forceFail = false; // 只失败一次
            log.error("[Saga-库存] 模拟故障：扣库存失败 orderId={}", orderId);
            throw new RuntimeException("库存服务不可用（模拟故障）");
        }

        Integer current = stock.getOrDefault(productId, 0);
        if (current < quantity) {
            log.warn("[Saga-库存] 库存不足 orderId={} productId={} 需要={} 剩余={}",
                    orderId, productId, quantity, current);
            return false;
        }

        stock.put(productId, current - quantity);
        log.info("[Saga-库存] ✅ 扣库存成功 orderId={} productId={} 扣减={} 剩余={}",
                orderId, productId, quantity, stock.get(productId));
        return true;
    }

    /**
     * 补偿：加回库存（对应 reserveInventory 的补偿操作）。
     * 补偿操作本身应该是幂等的——重复调用不会重复加库存。
     */
    public void releaseInventory(Long orderId, String productId, int quantity) {
        simulateNetworkDelay("加回库存(补偿)");
        stock.merge(productId, quantity, Integer::sum);
        log.info("[Saga-补偿] 🔄 库存已加回 orderId={} productId={} 加回={} 当前={}",
                orderId, productId, quantity, stock.get(productId));
    }

    /** 查询当前库存（观察补偿效果用） */
    public int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }

    /** 注入故障：下次扣库存抛异常 */
    public void forceFailNext() {
        this.forceFail = true;
        log.warn("[Saga-库存] ⚠️ 已设置下次扣库存强制失败");
    }

    private void simulateNetworkDelay(String operation) {
        try {
            Thread.sleep(300 + (long) (Math.random() * 400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.debug("[Saga-库存] {} 完成（模拟网络延迟）", operation);
    }
}
