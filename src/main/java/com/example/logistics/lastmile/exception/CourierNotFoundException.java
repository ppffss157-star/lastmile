package com.example.logistics.lastmile.exception;

/**
 * 配送员不存在异常 → HTTP 404
 */
public class CourierNotFoundException extends RuntimeException {

    public CourierNotFoundException() {
        super("配送员不存在");
    }

    public CourierNotFoundException(String message) {
        super(message);
    }
}
