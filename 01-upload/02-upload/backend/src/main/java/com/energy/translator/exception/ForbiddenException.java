package com.energy.translator.exception;

/**
 * 无权限/禁止访问异常 → HTTP 403
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
