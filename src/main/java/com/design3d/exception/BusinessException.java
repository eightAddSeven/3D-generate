package com.design3d.exception;

import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }

    public static BusinessException notFound(String resource, String id) {
        return new BusinessException(404, resource + " 不存在: " + id);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }
}
