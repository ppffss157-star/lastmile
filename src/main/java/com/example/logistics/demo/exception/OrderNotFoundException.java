package com.example.logistics.demo.exception;

/**
 * 订单不存在异常 → HTTP 404
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException() {
        super("订单不存在");
    }

    public OrderNotFoundException(String message) {
        super(message);
    }
}
