package com.yupi.yupicturebackend.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserAuthConfig;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserRole;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SpaceUserAuthManager {

    // 引入UserService
    @Resource
    private UserService userService;

    // 引入SpaceUserService
    @Resource
    private SpaceUserService spaceUserService;

    // 定义常量SPACE_USER_AUTH_CONFIG
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    //
    static {
        // 使用Hutool的ResourceUtil.readUtf8Str读取配置类
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        // 然后通过JSONUtil的toBean方法把得到的json转为spaceUserAuthConfig.class
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }


    /**
     * 根据角色获取权限列表
     *
     * @param spaceUserRole
     * @return
     */
    public List<String> getPermissionByRole(String spaceUserRole) {
        // 校验参数，不存在就返回空ArrayList
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 否则先拿到所有的Roles，再过滤 当前要查的角色role等于角色列表中的Key
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles()
                .stream()
                .filter(r -> r.getKey().equals(spaceUserRole))
                // 如果找到了这条数据，就用.findFirst(),没找到就null
                .findFirst()
                .orElse(null);

        // 如果获取的role不存在（一般不存在这种情况），直接返回ArrayList
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }


    /**
     * 获取权限列表（目的是给前端一个权限表，降低后端压力）
     *
     * @param space
     * @param loginUser
     * @return
     */
    public List<String> getPermissionList(Space space, User loginUser) {
        if (loginUser == null) {
            return new ArrayList<>();
        }
        // 1.设置管理员权限
        List<String> ADMIN_PERMISSIONS = getPermissionByRole(SpaceRoleEnum.ADMIN.getValue());

        // 2. 公共图库
        if (space == null) {
            if (userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            }
            // 不是管理员，可以返回空权限，也可以返回只读权限
            // return new ArrayList<>();
            return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
        }

        // 3. 判断当前空间类别
        // 从当前空间中拿到SpaceType对应SpaceTypeEnum的值
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return new ArrayList<>();
        }
        // 根据空间获取对应的权限 -> 使用switch语句
        switch (spaceTypeEnum) {
            case PRIVATE:
                // 私有空间，仅本人或管理员有所有权限
                if (space.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    return new ArrayList<>();
                }
            case TEAM:
                // 团队空间：查询SpaceUser并获取角色和权限
                SpaceUser spaceUser = spaceUserService.lambdaQuery()
                        // 查询用户所在的空间
                        .eq(SpaceUser::getSpaceId, space.getId())
                        // 查询用户的信息
                        .eq(SpaceUser::getUserId, loginUser.getId())
                        .one();
                if (spaceUser == null) {
                    return new ArrayList<>();
                } else {
                    // 如果spaceUser存在，就拿到对应的角色权限
                    return getPermissionByRole(spaceUser.getSpaceRole());
                }
        }
        // 如果什么空间都不是，才返回空数组
        return new ArrayList<>();
    }
}
