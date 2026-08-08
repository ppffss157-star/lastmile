package com.example.logistics.lastmile.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.logistics.lastmile.entity.DeadLetterMessage;
import com.example.logistics.lastmile.service.DeadLetterService;

import lombok.RequiredArgsConstructor;

/**
 * 死信管理 REST API —— 人工补偿入口。
 *
 * <h3>典型运维流程</h3>
 * <ol>
 *   <li>GET /api/dead-letters → 看到一堆死信</li>
 *   <li>排查根因（看日志、检查下游服务）</li>
 *   <li>修复后 → POST /api/dead-letters/{id}/republish?note=下游已恢复</li>
 *   <li>确认业务正常</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    /**
     * 查所有未处理的死信。
     */
    @GetMapping
    public ResponseEntity<List<DeadLetterMessage>> listUnhandled() {
        return ResponseEntity.ok(deadLetterService.listUnhandled());
    }

    /**
     * 按订单 ID 查死信。
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<DeadLetterMessage>> listByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(deadLetterService.listByOrderId(orderId));
    }

    /**
     * 重投单条死信到主队列。
     *
     * @param id   死信记录 ID
     * @param note 重投原因（必填，强制运维写清楚再操作）
     */
    @PostMapping("/{id}/republish")
    public ResponseEntity<DeadLetterMessage> republish(
            @PathVariable Long id,
            @RequestParam String note) {
        return ResponseEntity.ok(deadLetterService.republish(id, note));
    }

    /**
     * 批量重投所有未处理死信。
     */
    @PostMapping("/republish-all")
    public ResponseEntity<List<Map<String, Object>>> republishAll(@RequestParam String note) {
        return ResponseEntity.ok(deadLetterService.republishAll(note));
    }

    /**
     * 标记已处理（不重投，只是确认这条不需要处理了）。
     */
    @PostMapping("/{id}/mark-handled")
    public ResponseEntity<DeadLetterMessage> markHandled(
            @PathVariable Long id,
            @RequestParam String note) {
        return ResponseEntity.ok(deadLetterService.markHandled(id, note));
    }
}
