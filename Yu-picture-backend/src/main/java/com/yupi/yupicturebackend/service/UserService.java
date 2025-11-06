package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Karry178
 * @description 针对表【user(用户)】的数据库操作Service
 * @createDate 2025-11-05 16:28:51
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册的接口
     * 参数可以直接传已经写好的dto类：UserRegisterRequest，里面也是这三个参数
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 确认密码
     * @return 新用户id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);


    /**
     * 自定义一个加密方法,使用Encrypt算法,并打@Override注解，变成一个接口(为了让其成为公共方法使用)
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);


    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      登录用户请求
     * @return 登录成功
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user 用户信息
     * @return 脱敏后的loginUserVO
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 获取脱敏后的用户信息
     * 应用场景：普通用户查其他人信息
     *
     * @param user 用户信息
     * @return 脱敏后的UserVO
     */
    UserVO getUserVO(User user);


    /**
     * 获取脱敏后的用户信息列表(给管理员用的功能)
     *
     * @param userLst 用户信息
     * @return 脱敏后的UserVO列表
     */
    List<UserVO> getUserVOList(List<User> userLst);


    /**
     * 获取当前登录用户 -> 只在系统内部调用的方法，还没有到封装到前端的阶段
     *
     * @param request 登录用户请求
     * @return 返回当前登录用户信息
     */
    User getLoginUser(HttpServletRequest request);


    /**
     * 用户退出登录(或者说广义上的用户注销)
     *
     * @param request 登录请求
     * @return
     */
    boolean userLogout(HttpServletRequest request);


    /**
     * 【通用查表模板】获取查询条件 -> 作用：把Java对象转换为MyBatis需要的QueryWrapper
     *
     * @param userQueryRequest 用户查询请求
     * @return 查询结果
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
}
