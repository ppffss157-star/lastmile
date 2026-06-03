package com.example.logistics.demo.exception;

/**
 * 非法状态流转异常 → HTTP 400
 */
public class IllegalStatusTransitionException extends RuntimeException {

    public IllegalStatusTransitionException() {
        super("非法状态流转");
    }

    public IllegalStatusTransitionException(String message) {
        super(message);
    }
}
