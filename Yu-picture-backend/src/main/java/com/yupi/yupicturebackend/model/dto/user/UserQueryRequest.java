package com.yupi.yupicturebackend.model.dto.user;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 用户查询请求类 -> 以后查询类其实都可以继承 通用分页请求类 PageRequest
 *
 * @author Karry178
 */
@Data
// 这个注解作用是：
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 用户权限/角色：user/admin
     */
    private String userRole;

    private static final long serialVersionUID = 1L;
}
