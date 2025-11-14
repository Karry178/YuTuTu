package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.UserRoleEnum;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Karry178
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2025-11-05 16:28:51
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 用户注册的接口
     * 参数可以直接传已经写好的dto类：UserRegisterRequest，里面也是这三个参数
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 确认密码
     * @return 新用户id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1.校验参数
        // 可以使用Hutool工具类的hasBlank判断
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2.检查用户账号是否和数据库中已有的重复
        // 【重要】第一个接口，使用MyBatis-Plus提供的 baseMapper 写查询，原生的方法
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 保证QueryWrapper查到的字段 = 输入的字段
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            // 查到的数量大于0，说明有存在的数据了，重复了
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复了");
        }
        // 3.密码要加密！绝不能明文存储，加盐值
        String encryptPassword = getEncryptPassword(userPassword);
        // 4.插入数据到数据库中 - 先新建用户，设置各项属性后，调用Service中的save方法保存
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword); // 必须存加密后的密码！
        user.setUserName("无名"); // 设置一个用户的默认名称
        user.setUserRole(UserRoleEnum.USER.getValue()); // 直接从用户枚举类中拿到普通用户的枚举值即可

        boolean saveResult = this.save(user);
        if (!saveResult) {
            // 一般插入错误直接归类为 系统错误
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        /**
         * 5.注册成功的话，直接返回当前用户Id；
         * 为什么明明在上面没设置用户Id，却能拿到id？这是用了MyBatis-Plus的【主键回填】的功能
         *
         * 主键回填：在新用户插入数据库后，MySQL自动生成的自增主键id会被MyBatis-Plus自动填充回user对象中。
         */
        return user.getId();
    }


    /**
     * 自定义一个加密方法,并打@Override注解，变成一个接口(为了让其成为公共方法使用)
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐操作 - 盐值为Karry
        final String SALT = "Karry";
        // 可以直接用Spring的工具类实现 - DigestUtil.md5DigestAsHex()
        // 然后getBytes()的目的是什么？ 获取字符串的字节数，然后转换为md5加密后的结果
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }


    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @param request      登录用户请求
     * @return 登录成功
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1.校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }
        // 2.对用户传递的密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3.查询数据库中的用户是否存在 -> 不存在就抛出异常
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        // 使用baseMapper的selectOne方法，因为每次最多只查一个用户
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            /**
             * 原来log日志和定义的报错throw是可以一起输出的？这俩的作用、分工分别是什么呢？分别什么时候用上？
             * log.info()：记录日志到文件/控制台，给开发者看的，调试和追踪系统运行情况，程序继续执行
             * throw：抛异常，给调用者处理，并且中止当前方法执行，返回错误给前端用户
             */
            log.info("user login field, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 4.保存用户的登录态 -> 通过request请求通过前端穿的SessionId得到其唯一的Session空间,并给这个空间设置值
        // 为什么要设置常量呢？
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 5.调用脱敏方法，返回给前端脱敏后的用户登录信息
        return this.getLoginUserVO(user);
    }


    /**
     * 获取脱敏后的用户登录信息
     *
     * @param user 用户信息
     * @return 脱敏后的loginUserVO
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        // 校验
        if (user == null) {
            return null;
        }
        // 可以用Spring或者Hutool的BeanUtils中的copyProperties()方法映射复制,user -> loginUserVO
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }


    /**
     * 获取脱敏后的用户信息
     * 应用场景：普通用户查其他人信息
     *
     * @param user 用户信息
     * @return 脱敏后的UserVO
     */
    @Override
    public UserVO getUserVO(User user) {
        // 1.校验
        if (user == null) {
            return null;
        }
        // 2.新建一个userVO对象，把User 赋值到 UserVO
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }


    /**
     * 获取脱敏后的用户信息列表(给管理员用的功能)
     *
     * @param userList 用户列表信息
     * @return 脱敏后的UserVO列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        // 1.校验 -> 使用Spring或Hutool的工具类CollUtil判断列表是否为空，为空就返回空列表
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }

        // 2.使用stream流循环遍历用户,转为列表
        // 俩循环方法：map(user -> getUserVO(user)) 或 map(this::getUserVO)
        return userList.stream()
                .map(user -> getUserVO(user))
                .collect(Collectors.toList());
    }


    /**
     * 获取当前登录用户 -> 只在系统内部调用的方法，还没有到封装到前端的阶段
     *
     * @param request 登录用户请求
     * @return 返回当前登录用户信息
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 1.登录用户时已经保存了登录态Session，现在通过相同的KEY获取相同用户的登录态
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 给用户登录态类型强转成User类型，目的：
        User currentUser = (User) userObj;
        // 判断用户是否登录了
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 2.获取的登录态是Session缓存数据，如果数据库修改了内容，很可能查不到正确数据；
        // 最好是再查一遍数据库，缓存尽可能不去用获取数据
        Long userId = currentUser.getId();
        currentUser = this.getById(userId); // 再查一次数据库
        // 再判断一下数据库中是否还存在这条数据
        ThrowUtils.throwIf(currentUser == null, ErrorCode.NOT_LOGIN_ERROR);

        return currentUser;
    }


    /**
     * 用户退出登录(或者说广义上的用户注销)
     *
     * @param request 登录请求
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 1.判断用户是否已登录 - 从Session登录态验证
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        // 2.查询到登录了，移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }


    /**
     * 【通用查表模板】获取查询条件 -> 作用：把Java对象转换为MyBatis需要的QueryWrapper
     *
     * @param userQueryRequest 用户查询请求
     * @return 查询结果
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        // 1.校验
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 2.否则，拿到userQueryRequest的所有get方法，获取对象
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userAvatar = userQueryRequest.getUserAvatar();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        // 3.定义一个QueryWrapper，验证参数是否为空，获取参数
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        // 排序字段 按照升序排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    /**
     * 通用方法 - 判断用户是否为管理员
     *
     * @param user 当前用户
     * @return
     */
    @Override
    public boolean isAdmin(User user) {
        // 1.校验
        if (user == null) {
            return false;
        }
        if (UserRoleEnum.ADMIN.getValue().equals(user.getUserRole())) {
            return false;
        }
        return false;
    }

}




