package com.example.logistics.lastmile.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.logistics.lastmile.entity.MessageDedup;

/**
 * message_dedup 表 CRUD。
 * 主键 messageId 自带 unique key，重复插入会抛
 * {@link org.springframework.dao.DuplicateKeyException}。
 */
public interface MessageDedupRepository extends JpaRepository<MessageDedup, String> {

    /**
     * 清理超过指定天数的旧记录。
     * 定时任务调用，防止表无限膨胀。
     */
    @Modifying
    @Query("DELETE FROM MessageDedup m WHERE m.createdAt < :cutoff")
    int deleteOldRecords(@Param("cutoff") LocalDateTime cutoff);
}
