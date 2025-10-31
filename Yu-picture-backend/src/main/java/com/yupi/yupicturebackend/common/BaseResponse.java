package com.yupi.yupicturebackend.common;

import com.yupi.yupicturebackend.exception.ErrorCode;

import java.io.Serializable;

/**
 * 通用响应类
 * @param <T>
 * @author Karry178
 */
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    // 下面是三种全局通用相应

     /**
     * 生成构造函数 -> 数据、状态码和错误信息都有
     * @param data  数据
     * @param code 状态码
     * @param message 错误信息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }


    /**
     * 构造函数 -> 状态码和错误信息
     * @param code
     * @param data
     */
    public BaseResponse(int code, T data) {
        // 直接调用上面的构造函数
        this(code, data, "");
    }


    /**
     * 直接从错误码获取信息
     * @param errorCode 错误码
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
