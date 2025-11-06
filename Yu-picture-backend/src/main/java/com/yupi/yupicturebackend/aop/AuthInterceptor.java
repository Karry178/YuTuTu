package com.yupi.yupicturebackend.aop;

import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * AOP切面
 * AuthInterceptor：权限拦截器
 *
 * @Aspect 表示这是一个切面
 * @Component 表示这是SpringBoot的Bean，被识别到
 */
@Aspect
@Component
public class AuthInterceptor {

    // 为了权限校验，先获取用户信息，引入UserService
    @Resource
    private UserService userService;


    /**
     * AOP切面：doInterceptor目的是对指定方法进行拦截
     * public 这个方法称为 切点
     *
     * @param joinPoint 切点，可以根据joinPoint知道对哪个方法进行了拦截
     * @param authCheck 标记的注解，有这个注解的才会被拦截
     * @return
     * @Around 表示环绕切面，可以在某个方法/切点 执行前后都可以执行一定的逻辑
     * @Around("@annotation(authCheck)") 表示在切点/方法上必须打了authCheck这个注解的才会被执行拦截校验
     * <p>
     * AOP切面 vs GlobalExceptionHandler 区别：
     * AOP切面：针对方法的拦截，可以在方法执行前后都可以执行一定的逻辑，判断用户权限是否满足，满足才放行执行
     * GlobalExceptionHandler：针对所有异常的拦截，可以统一处理所有异常，返回统一格式的响应，在方法执行完抛出异常才触发
     * 形象比喻：AOP是门卫（进门前检查），GlobalExceptionHandler是救护车（出事后处理）。
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {

        // 1.首先获得用户必须的权限
        String mustRole = authCheck.mustRole();
        // 2.拿到登录用户属性 -> RequestContextHolder
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        // 3.把拿到的requestAttributes转为ServletRequestAttributes -> 目的是转为熟悉的HttpServletRequest
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 4.然后通过UserService的getLoginUser方法获取登录用户信息
        User loginUser = userService.getLoginUser(request);

        // 5.将字符串mustRole调用枚举类UserRoleEnum转为枚举值
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 6.如果得到的枚举值为空，说明其不需要权限就能查，直接放行即可
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 以下的代码：必须有权限才会通过！

        // 1.依旧转为枚举类，获取当前登录用户的用户角色 -> 并获取登录用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 2.如果无权限，抛出无权限异常
        ThrowUtils.throwIf(userRoleEnum == null, ErrorCode.NO_AUTH_ERROR);

        // 要求必须有管理员权限，但如果用户无管理员权限，直接拒绝
        // 前面是必须是管理员权限的用户，后面是用户没有管理员权限
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 否则用户是管理员了
        return joinPoint.proceed();
    }
}
