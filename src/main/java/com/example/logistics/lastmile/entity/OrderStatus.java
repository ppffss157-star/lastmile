package com.example.logistics.lastmile.entity;

public enum OrderStatus {
    PENDING,      // Saga: 订单已创建但未确认，等待库存和支付结果
    CREATED,      // Saga 全部成功，订单确认
    ASSIGNED,
    DELIVERING,
    COMPLETED,
    CANCELLED
}