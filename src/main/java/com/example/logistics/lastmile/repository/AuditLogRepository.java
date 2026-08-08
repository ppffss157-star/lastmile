package com.example.logistics.lastmile.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.logistics.lastmile.entity.AuditLog;

/**
 * 审计日志仓库。
 * 只提供查询接口，写入由 {@code AuditLogAspect} 负责。
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** 查某个用户的操作记录，按时间倒序 */
    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    /** 查某个时间段内的操作记录 */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);
}
