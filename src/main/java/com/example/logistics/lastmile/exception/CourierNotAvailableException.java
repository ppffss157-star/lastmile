package com.example.logistics.lastmile.exception;

/**
 * 配送员不可接单异常 → HTTP 409
 */
public class CourierNotAvailableException extends RuntimeException {

    public CourierNotAvailableException() {
        super("配送员当前不可接单");
    }

    public CourierNotAvailableException(String message) {
        super(message);
    }
}
