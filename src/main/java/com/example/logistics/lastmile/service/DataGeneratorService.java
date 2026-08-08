package com.example.logistics.lastmile.service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 100w 订单数据生成器 —— 用于 EXPLAIN 慢 SQL 实战。
 *
 * <h2>用法</h2>
 * 启动时加 {@code --spring.profiles.active=dev,data-gen} 触发。
 * 或者在 application-dev.yml 里把 data-gen 加到 spring.profiles.include。
 *
 * <h2>技术选型：为什么不用 JPA saveAll？</h2>
 * <p>
 * JPA 的 saveAll 每条 insert 都是独立的 SQL，100w 条 = 100w 次网络往返，
 * 慢到你怀疑人生（十几分钟）。JDBC batch insert 把多条 SQL 攒一起发，
 * 100w 条约 2~3 分钟跑完。
 * </p>
 *
 * <h2>数据分布（模拟真实场景）</h2>
 * <ul>
 *   <li>状态：CREATED 40%、DELIVERING 25%、COMPLETED 25%、ASSIGNED 5%、CANCELLED 5%</li>
 *   <li>时间：过去 365 天内随机分布</li>
 *   <li>快递员：1~1000 随机（前提 courier 表有 1000 条）</li>
 * </ul>
 */
@Service
@Profile("data-gen")   // 只在指定 profile 时触发，日常开发不会意外跑
public class DataGeneratorService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorService.class);

    private final JdbcTemplate jdbcTemplate;

    /** 总行数 */
    private static final int TOTAL_ROWS = 1_000_000;

    /** 每批插入行数（太大 OOM，太小慢，5000 是个甜点） */
    private static final int BATCH_SIZE = 5000;

    private static final String[] SURNAMES = {"张","李","王","刘","陈","杨","赵","黄","周","吴",
                                               "徐","孙","胡","朱","高","林","何","郭","马","罗"};
    private static final String[] GIVEN_NAMES = {"伟","芳","娜","敏","静","丽","强","磊","军",
                                                  "洋","勇","艳","杰","涛","明","超","华","兵","刚"};
    private static final String[] ROADS = {"中山路","解放路","人民路","建设路","和平路",
                                            "文化路","长安街","南京路","北京路","朝阳路"};
    private static final String[] PHONE_PREFIX = {"138","139","158","186","137"};
    private static final String[] STATUSES = {
        "CREATED","CREATED","CREATED","CREATED",        // 40% — 4/10
        "DELIVERING","DELIVERING","DELIVERING",          // 30% — 3/10 (调高让查询有数据)
        "COMPLETED","COMPLETED",                          // 20% — 2/10
        "CANCELLED"                                       // 10% — 1/10
        // ASSIGNED 很少见，略过
    };

    public DataGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 应用启动时自动执行。
     * 先补快递员，再补订单。已满则跳过。
     */
    @Override
    @Transactional
    public void run(String... args) {
        // 先确保快递员够 1000 个
        seedCouriers();

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        if (count != null && count >= TOTAL_ROWS) {
            log.info("📊 orders 表已有 {} 条数据，跳过造数", count);
            return;
        }

        log.info("🔄 开始造数：{} 条，每批 {} 条", TOTAL_ROWS, BATCH_SIZE);
        long start = System.currentTimeMillis();

        // 关掉外键检查，批量插入速度翻倍
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        int inserted = 0;
        while (inserted < TOTAL_ROWS) {
            int batchSize = Math.min(BATCH_SIZE, TOTAL_ROWS - inserted);
            inserted += batchInsert(batchSize);

            if (inserted % 100_000 == 0) {
                long elapsed = (System.currentTimeMillis() - start) / 1000;
                log.info("  ⏳ 已插入 {} / {} ({}%), 耗时 {}s",
                        inserted, TOTAL_ROWS,
                        inserted * 100 / TOTAL_ROWS, elapsed);
            }
        }

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("✅ 造数完成！ {} 条，总耗时 {}s，均速 {} 条/s",
                TOTAL_ROWS, elapsed, TOTAL_ROWS / Math.max(elapsed, 1));
    }

    /**
     * 确保 courier 表有 1000 条数据（外键需要）。
     */
    private void seedCouriers() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM courier", Integer.class);
        if (count != null && count >= 1000) {
            return;
        }
        log.info("📦 courier 表只有 {} 条，补到 1000 条...", count);

        String sql = "INSERT IGNORE INTO courier (id, name, phone, status) VALUES (?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            batch.add(new Object[]{
                    i,
                    "快递员" + i,
                    "138" + String.format("%08d", i),
                    "AVAILABLE"
            });
        }
        jdbcTemplate.batchUpdate(sql, batch);
        log.info("✅ courier 表已补齐到 1000 条");
    }

    /**
     * 批量插入一批数据。
     * 用 PreparedStatement + addBatch + executeBatch，JDBC 驱动自动攒批发送。
     */
    private int batchInsert(int size) {
        String sql = "INSERT INTO orders (customer_name, address, phone, status, courier_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new ArrayList<>(size) {{
            for (int i = 0; i < size; i++) {
                add(new Object[]{
                        randomName(),
                        randomAddress(),
                        randomPhone(),
                        randomStatus(),
                        randomCourierId(),
                        Timestamp.valueOf(randomCreatedAt())
                });
            }
        }});

        return size;
    }

    // ==================== 随机数据生成 ====================

    private String randomName() {
        return SURNAMES[ThreadLocalRandom.current().nextInt(SURNAMES.length)]
                + GIVEN_NAMES[ThreadLocalRandom.current().nextInt(GIVEN_NAMES.length)];
    }

    private String randomAddress() {
        return ROADS[ThreadLocalRandom.current().nextInt(ROADS.length)]
                + ThreadLocalRandom.current().nextInt(1, 501) + "号";
    }

    private String randomPhone() {
        return PHONE_PREFIX[ThreadLocalRandom.current().nextInt(PHONE_PREFIX.length)]
                + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    private String randomStatus() {
        return STATUSES[ThreadLocalRandom.current().nextInt(STATUSES.length)];
    }

    private Long randomCourierId() {
        return (long) ThreadLocalRandom.current().nextInt(1, 1001);
    }

    private LocalDateTime randomCreatedAt() {
        return LocalDateTime.now()
                .minusDays(ThreadLocalRandom.current().nextInt(365))
                .minusHours(ThreadLocalRandom.current().nextInt(24))
                .minusMinutes(ThreadLocalRandom.current().nextInt(60));
    }
}
