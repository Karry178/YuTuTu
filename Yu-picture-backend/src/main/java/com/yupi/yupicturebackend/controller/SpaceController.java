package com.yupi.yupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.DeleteRequest;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.auth.SpaceUserAuthManager;
import com.yupi.yupicturebackend.model.constant.UserConstant;
import com.yupi.yupicturebackend.model.dto.space.*;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {

    // 引入UserService
    @Resource
    private UserService userService;

    // 引入SpaceService
    @Resource
    private SpaceService spaceService;

    // 引入SpaceUserAuthManager，目的是获取权限列表PermissionList
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;


    // 【增】新增空间
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {

        // 1.校验参数
        ThrowUtils.throwIf(spaceAddRequest == null, ErrorCode.PARAMS_ERROR);
          // 获取当前登录用户信息
        User loginUser = userService.getLoginUser(request);
          // 调用Service层的addSpace方法
        long newId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(newId);
    }


    /**
     * 【删】根据空间id删除
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 1.校验参数
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        // 2.判断用户是否可以删除空间space
          // 2.1 先获取登录用户信息
        User loginUser = userService.getLoginUser(request);
          // 2.2 再根据id判断space是否存在
        Long id = deleteRequest.getId();

        // 3.从数据库根据space的id获取space => 如果space存在，定义为oldSpace；不存在就报错
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null,ErrorCode.NOT_FOUND_ERROR, "要删除的空间不存在");

        // 4.如果查到的oldSpace存在了，只允许管理员和本人删除
        spaceService.checkSpaceAuth(loginUser, oldSpace);
        /*if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            // 不是这两个角色，就报无权限错误，不允许删除
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你无权限删除");
        }*/

        // 5.最后一步：操作数据库，删除查到的space
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    // 【改-仅管理员可以更新空间】
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest, HttpServletRequest request) {

        // 1.参数校验
        ThrowUtils.throwIf(spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        // 2.把dto类的值赋值给实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据，调用spaceService的fillSpaceBySpaceLevel方法
        spaceService.fillSpaceBySpaceLevel(space);

        // 3.数据校验
          // 调用validSpace方法, 且 非创建 操作，第二个参数为false
        spaceService.validSpace(space, false);

        // 4.判断要修改的space是否存在
        Long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 5.最后操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 【查】根据id获取空间（仅管理员可用）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0,ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(space);
    }


    /**
     * 【查】根据id获取空间
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0,ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);

        // 【团队空间】获取当前登录用户信息并返回VO
        SpaceVO spaceVO = spaceService.getSpaceVO(space, request);
        User loginUser = userService.getLoginUser(request);
        // 然后调用spaceUserAuthManager的getPermissionList方法获取权限列表
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        // 把得到的permissionList设置到spaceVO里
        spaceVO.setPermissionList(permissionList);

        // 获取VO封装类
        return ResultUtils.success(spaceVO);
    }


    // 【改】编辑空间(给用户使用)
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest, HttpServletRequest request) {

        // 1.参数校验
        ThrowUtils.throwIf(spaceEditRequest == null || spaceEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);

        // 2.在此处进行实体类与DTO请求类的转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 设置编辑时间
        space.setEditTime(new Date());

        // 3.数据校验
        spaceService.validSpace(space, false);
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 判断要编辑的space是否存在，通过id
        Long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 4.【权限】只有本人和管理员可以编辑space -> 调用SpaceService的checkSpaceAuth()方法
        spaceService.checkSpaceAuth(loginUser, oldSpace);

        // 5.操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 分页获取空间列表(仅管理员可用)
     * @param spaceQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        // 1.先获取当前页码数与每页最大条数
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();

        // 2.限制爬虫，每页大于20条，报错
        ThrowUtils.throwIf(size >= 20, ErrorCode.PARAMS_ERROR);

        // 3.查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size), spaceService.getQueryWrapper(spaceQueryRequest));

        return ResultUtils.success(spacePage);
    }


    /**
     * 分页获取空间列表
     * @param spaceQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest, HttpServletRequest request) {
        // 1.先获取当前页码数与每页最大条数
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();

        // 2.限制爬虫，每页大于20条，报错
        ThrowUtils.throwIf(size >= 20, ErrorCode.PARAMS_ERROR);

        // 3.查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size), spaceService.getQueryWrapper(spaceQueryRequest));

        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }


    /**
     * 直接获取空间级别列表，便于前端展示
     * @return
     */
    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> {
                    return new SpaceLevel(
                            spaceLevelEnum.getValue(),
                            spaceLevelEnum.getText(),
                            spaceLevelEnum.getMaxCount(),
                            spaceLevelEnum.getMaxSize()
                    );
                }).collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }
}
