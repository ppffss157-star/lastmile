package com.example.logistics.lastmile.exception;

/**
 * 配送员被锁定异常（分布式锁冲突） → HTTP 423
 */
public class CourierLockedException extends RuntimeException {

    public CourierLockedException() {
        super("配送员正在被其他订单派单中，请稍后再试");
    }

    public CourierLockedException(String message) {
        super(message);
    }
}
