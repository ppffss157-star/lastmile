package com.example.order.entity;

public enum OrderStatus {
    PENDING,      // Saga 中间态
    CREATED,      // 已确认
    ASSIGNED,     // 已分配配送员
    DELIVERING,   // 配送中
    COMPLETED,    // 已完成
    CANCELLED     // 已取消
}
