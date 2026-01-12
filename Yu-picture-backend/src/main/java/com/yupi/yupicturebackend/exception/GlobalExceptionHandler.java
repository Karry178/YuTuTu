package com.yupi.yupicturebackend.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
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
     * 将Sa-Token的异常转为我们自定义的全局异常
     * NotLoginException是Sa-Token提供的未登录异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }


    /**
     * 将Sa-Token的异常转为我们自定义的全局异常
     * NotPermissionException 是是Sa-Token提供的无权限异常
     *
     * @param e
     * @return
     */
    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionException(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }


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
