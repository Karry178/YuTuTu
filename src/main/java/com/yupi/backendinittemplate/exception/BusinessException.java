package com.yupi.backendinittemplate.exception;

import lombok.Getter;

/**
 * 自定义异常类
 * @author Karry178
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;


    /**
     * 错误信息
     * @param code 错误码
     * @param message 错误信息
     */
    public BusinessException(int code, String message) {
        super(message); // 继承父类构造函数，给message赋值
        this.code = code;
    }

    /**
     * 从错误码获取错误信息
     * @param errorCode 错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 错误码和错误信息
     * @param errorCode 错误码
     * @param message 错误信息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}
