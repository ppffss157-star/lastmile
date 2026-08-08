package com.example.logistics.lastmile.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.logistics.lastmile.repository.OrderRepository;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import lombok.extern.slf4j.Slf4j;

/**
 * <h2>布隆过滤器：穿透的第一道防线</h2>
 * <p>
 * <b>要解决的问题：</b>恶意/错误请求用不存在的 ID 疯狂查询，缓存永远不命中，每次都打 DB。
 * </p>
 *
 * <h3>布隆过滤器怎么工作的？</h3>
 * <ol>
 *   <li>一个位数组 + N 个哈希函数</li>
 *   <li>写入时：对 key 算 N 个哈希，把对应的 N 个位都标成 1</li>
 *   <li>查询时：对 key 算 N 个哈希，<b>如果任何一位是 0 → 绝对不存在</b>；
 *       如果全部是 1 → <b>可能存在</b>（有误判，但不会漏判）</li>
 * </ol>
 *
 * <h3>关键参数</h3>
 * <ul>
 *   <li>expectedInsertions = 10 万 → 预估最多存多少个 ID</li>
 *   <li>fpp = 0.01 → 1% 误判率（不存在却说可能存在）</li>
 *   <li>两者决定位数组大小和哈希函数数量，越大/越低 → 越占内存但越准</li>
 * </ul>
 *
 * <h3>Trade-off</h3>
 * 能加不能删（Guava 实现），订单删除后过滤器中 ID 还在，最多多查一次 DB，不影响正确性。
 */
@Slf4j
@Configuration
public class BloomFilterConfig {

    @Bean
    public BloomFilter<Long> orderIdBloomFilter(OrderRepository orderRepository) {
        List<Long> allIds = orderRepository.findAllIds();
        int expectedSize = Math.max(allIds.size(), 10000);

        BloomFilter<Long> filter = BloomFilter.create(
                Funnels.longFunnel(),
                expectedSize,   // 预计插入数量
                0.01);          // 1% 误判率

        allIds.forEach(filter::put);
        log.info("布隆过滤器初始化完成，已加载 {} 个订单 ID，误判率 1%", allIds.size());
        return filter;
    }
}
