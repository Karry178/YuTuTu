package com.yupi.yupicturebackend.exception;

import lombok.Getter;

/**
 * 枚举错误码
 * @author Karry178
 */
@Getter
public enum ErrorCode {

    SUCCESS(0,"ok"),
    PARAMS_ERROR(40000,"请求参数错误"),
    NOT_LOGIN_ERROR(40100,"未登录"),
    NO_AUTH_ERROR(40101,"无权限"),
    NOT_FOUND_ERROR(40400,"请求数据不存在"),
    FORBIDDEN_ERROR(40300,"禁止访问"),
    SYSTEM_ERROR(50000,"系统内部异常"),
    OPERATION_ERROR(50001,"操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 状态码信息
     */
    private final String message;

    /**
     * 构造状态码函数
     * @param code 状态码
     * @param message 状态码信息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
