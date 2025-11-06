package com.yupi.yupicturebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.user.*;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.UserService;
import net.bytebuddy.implementation.bytecode.Throw;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

// @RestController 表示按照Restful风格返回数据的接口类
@RestController
// @RequestMapping("/") 表示该类下的所有接口，都以/开头
@RequestMapping("/user")
public class UserController {

    // 使用@Resource注解，引入UserService
    @Resource
    private UserService userService;


    /**
     * 用户注册接口
     *
     * @param userRegisterRequest 自己定义的用户注册请求，因为是一个对象，所以要加上@RequestBody
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        // 1.判断参数是否为空值 -> 使用ThrowUtils方法
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.如果参数不为空，拿到用户注册请求中的参数值
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        // 3.【重要】调用Service中的用户注册接口，并传入对应的值 操作
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }


    /**
     * 登录用户
     *
     * @param userLoginRequest 用户登录请求类
     * @param request          登录请求
     * @return 返回loginUsrVO给前端脱敏后的结果
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        // 1.判断参数是否为空值
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        // 2.如果有值，拿到用户登录请求的参数值
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        // 3.调用Service层用户登录接口，传入对应的值
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }


    /**
     * 前端获取当前登录用户，这一步就是前端获取了，返回给前端loginUserVO即可
     *
     * @param request 登录请求
     * @return loginUserVO
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        // 1.调用Service层的getLoginUser方法
        User loginUser = userService.getLoginUser(request);
        // 2.成功就返回结果：通过UserService层的脱敏方法loginUserVO返回
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }


    /**
     * 用户退出登录
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        // 加入校验，验证request是否为空值
        ThrowUtils.throwIf(request == null, ErrorCode.NOT_LOGIN_ERROR);
        // 调用userService中的userLogout接口
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 【增】添加用户
     * @param userAddRequest 新增用户请求
     * @return 用户id
     */
    @PostMapping("/add")
    // AOP切面：一定要明确增加用户的权限，只有管理员可以添加
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        // 1.校验参数
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 2.新建User对象，然后把原始值userAddRequest赋值给目标值user
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);

        // 3.赋值给user后，再给user一些默认值
          // 默认密码统一为1-8，但是要先使用加盐加密的方法encryptPassword()再保存数据库
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);

        // 4.调用UserService，保存用户到数据库,最后返回保存的用户id
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "添加用户失败");
        return ResultUtils.success(user.getId());
    }


    /**
     * 【查】根据id获取用户(仅管理员)
     * @param id 用户id
     * @return 用户信息
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        // 1.校验
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);

        // 2.从UserService中根据id获取用户信息
        User user = userService.getById(id);
          // 如果获取的用户不存在，报错：请求参数不存在
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }


    /**
     * 【增】根据用户id获取包装类，脱敏后返回给用户
     * @param id 用户id
     * @return 脱敏后的用户信息
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        // 1.根据id查询用户信息可以直接调用上面getUserById的方法，不用重写
        BaseResponse<User> response = getUserById(id);

        // 2.从上面获取的用户信息response中获取数据，最后调用UserService中脱敏方法返回给用户
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }


    /**
     * 【删】根据通用删除请求删除用户 [只有管理员可以删除]
     * @param deleteRequest 通用删除请求
     * @return 是否删除
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        // 1.校验
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.否则，调用UserService中remove方法，按id删除用户
        boolean removeById = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(removeById);
    }


    /**
     * 【改】根据用户更新请求 更新用户信息 [只可以管理员更新]
     * @param userUpdateRequest 用户更新请求
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        // 1.校验
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2.新建User对象，并把userUpdateRequest赋值给user
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);

        // 3.调用UserService，传入新用户的信息，更新用户信息
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 【重要 - 分页查询】
     * 先拿到当前页数和每页最大条数的参数信息，然后创建一个Page对象，并且新建一个返回给前端的PageVO对象
     * 取出原本的userList表转换为userListVO表，最后返回分页后的userVOList分页表
     * @param userQueryRequest 用户查询请求
     * @return userVOPage 返回给前端的用户分页表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        // 1.参数校验
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);

        // 2.从userQueryRequest获取current(当前页数)和pageSize(每页最大条数)，这俩是从PageRequest取出的，但是UserQueryRequest继承自PageRequest
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();

        // 3.从MyBatis新建一个Page对象（MyBatis-Plus提供的方法）
          // 先创建一个new Page,参数是当前页数和每页最大条数
          // 第二个是查询条件！调用UserService中的getQueryWrapper()方法
        Page<User> userPage = userService.page(new Page<>(current, pageSize), userService.getQueryWrapper(userQueryRequest));

        // 4.新建一个返回给前端的分页userVOPage,且传入参数
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());

        // 5.取出原本的userList，转为需要的userVOList
          // getRecords()返回的是查询返回的列表；再通过Service层的getUserVOList方法转换
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());

        // 6.最后再返回一个已有的分页类，把转化后的userVOList通过userVOPage.setRecords()方法返回分页
        userVOPage.setRecords(userVOList);

        return ResultUtils.success(userVOPage);
    }
}
