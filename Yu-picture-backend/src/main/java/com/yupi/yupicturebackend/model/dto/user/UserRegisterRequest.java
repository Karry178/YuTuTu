package com.yupi.yupicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求
 * dto 数据传输对象
 * 一般用来接收前端传来的请求参数，或者多个Service层之间需要传递的参数
 */
@Data
public class UserRegisterRequest implements Serializable {

    /**
     * 序列化UID
     */
    private static final long serialVersionUID = 8735650154179439661L;


    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

}
