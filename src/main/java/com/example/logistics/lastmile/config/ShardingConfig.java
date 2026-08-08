package com.example.logistics.lastmile.config;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.infra.config.algorithm.AlgorithmConfiguration;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.keygen.KeyGenerateStrategyConfiguration;
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariDataSource;

/**
 * 分表配置 —— 仅在 sharding profile 激活时生效。
 *
 * <h2>架构</h2>
 * <pre>
 * JPA / JDBC
 *     ↓
 * shardingDataSource (@Primary)      ← 逻辑表 orders，自动路由
 *     ↓
 * rawDataSource                      ← 直连 MySQL，ShardingSphere 底层
 *     ↓
 * MySQL (logistics_db)
 *     ├── orders_0
 *     ├── orders_1
 *     ├── orders_2
 *     └── orders_3
 *
 * Flyway  ← rawDataSource（绕过 ShardingSphere 直接建表）
 * </pre>
 *
 * <h2>分片策略</h2>
 * <ul>
 *   <li>分片键：id（Snowflake 全局唯一）</li>
 *   <li>分片算法：id % 4 → orders_0 ~ orders_3</li>
 * </ul>
 */
@Configuration
@Profile("sharding")
public class ShardingConfig {

    private static final Logger log = LoggerFactory.getLogger(ShardingConfig.class);

    /**
     * 直连 MySQL 的原始数据源。
     * 只被 Flyway 和 ShardingSphere 底层引用，不给 JPA 直接使用。
     */
    @Bean
    DataSource rawDataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        log.info("✅ Flyway 专用数据源已创建（直连 MySQL）");
        return ds;
    }

    /**
     * ShardingSphere 包装后的数据源 —— JPA / JDBC 都用这个。
     * <p>
     * 对上层代码完全透明：写 {@code SELECT * FROM orders}，
     * ShardingSphere 自动改写成查对应分片。
     * </p>
     */
    @Bean
    @Primary
    DataSource shardingDataSource(@Qualifier("rawDataSource") DataSource rawDs) throws SQLException {
        // 1. 配置分片规则
        ShardingRuleConfiguration shardingRule = new ShardingRuleConfiguration();

        // orders 表：4 片，按 id % 4 路由
        ShardingTableRuleConfiguration ordersRule =
                new ShardingTableRuleConfiguration("orders", "ds0.orders_${0..3}");

        // 表路由策略
        ordersRule.setTableShardingStrategy(
                new StandardShardingStrategyConfiguration("id", "orders_inline"));

        // Snowflake 主键（覆盖 JPA 的 @GeneratedValue(IDENTITY)）
        ordersRule.setKeyGenerateStrategy(
                new KeyGenerateStrategyConfiguration("id", "snowflake"));

        shardingRule.getTables().add(ordersRule);

        // 分片算法：INLINE 表达式
        Properties inlineProps = new Properties();
        inlineProps.setProperty("algorithm-expression", "orders_${id % 4}");
        shardingRule.getShardingAlgorithms().put("orders_inline",
                new AlgorithmConfiguration("INLINE", inlineProps));

        // Snowflake 主键生成器
        shardingRule.getKeyGenerators().put("snowflake",
                new AlgorithmConfiguration("SNOWFLAKE", new Properties()));

        // 2. 创建 ShardingSphere 数据源
        Properties props = new Properties();
        props.setProperty("sql-show", "false");

        DataSource shardingDs = ShardingSphereDataSourceFactory.createDataSource(
                Collections.singletonMap("ds0", rawDs),
                Collections.singletonList(shardingRule),
                props);

        log.info("✅ ShardingSphere 数据源已创建：orders → orders_0/1/2/3, Snowflake ID");
        return shardingDs;
    }

    /**
     * 分片模式下的 Flyway —— 必须用 rawDataSource 绕过 ShardingSphere。
     * 否则 Flyway 会看到 ShardingSphere 的逻辑视图而找不到物理表。
     */
    @Bean
    Flyway flyway(@Qualifier("rawDataSource") DataSource rawDs) {
        Flyway flyway = Flyway.configure()
                .dataSource(rawDs)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        log.info("✅ Flyway 迁移完成（使用直连 MySQL，不经 ShardingSphere）");
        return flyway;
    }
}
