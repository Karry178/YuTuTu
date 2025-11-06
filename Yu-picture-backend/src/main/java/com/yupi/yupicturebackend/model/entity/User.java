package com.yupi.yupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/**
 * 用户
 *
 * @TableName user
 */
@TableName(value = "user")
@Data
public class User implements Serializable {
    /**
     * id
     *
     * @ TableId(type = IdType.AUTO) 作用是 让id根据1、2、3...自动生成
     * @ TableId(type = IdType.ASSIGN_ID) 作用是 让MyBatis自动生成较长的Id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户角色:user/admin
     */
    private String userRole;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除(逻辑删除)
     * 使用@TableLogic目的是注明逻辑删除字段
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 加上 @ TableField(exist = false) 作用是
     */
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}