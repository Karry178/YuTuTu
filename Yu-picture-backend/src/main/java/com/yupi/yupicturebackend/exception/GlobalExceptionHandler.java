package com.yupi.yupicturebackend.exception;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 -> 统一拦截所有异常
 * @author Karry178
 */
// @RestControllerAdvice 环绕切面 -> 作用：统一拦截所有异常
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 统一处理业务异常
     * @param e 业务异常
     * @return 给前端错误码和信息的响应
     */
    // @ExceptionHandler(BusinessException.class) 目的是拦截所有BusinessException
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }


    /**
     * 对运行异常拦截，统一返回系统错误码
     * @param e 运行异常
     * @return 给前端错误码和信息的响应
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
