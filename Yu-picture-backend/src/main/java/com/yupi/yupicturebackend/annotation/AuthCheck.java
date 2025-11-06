package com.yupi.yupicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AOP切面的权限校验注解
 * 大多数注解类 都是写这俩注解
 *
 * @author Karry178
 * @Target(ElementType.METHOD) 作用是给注解打上范围，即针对方法打的注解
 * @Retention(RetentionPolicy.RUNTIME) 作用是注解在运行时生效
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须具有某个角色
     *
     * @return
     */
    String mustRole() default "";
}
