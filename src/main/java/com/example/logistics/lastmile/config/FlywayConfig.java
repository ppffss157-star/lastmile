package com.example.logistics.lastmile.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway 手动配置。
 * Spring Boot 4.0.5 移除了 FlywayAutoConfiguration，
 * 需要自己创建 Flyway Bean 来触发数据库迁移。
 *
 * 分片模式（sharding profile）下由 ShardingConfig 接管 Flyway，
 * 确保 Flyway 使用直连 MySQL 绕过 ShardingSphere。
 */
@Configuration
@Profile("!sharding")
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }
}
