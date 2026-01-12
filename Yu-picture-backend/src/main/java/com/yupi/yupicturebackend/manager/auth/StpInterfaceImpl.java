package com.yupi.yupicturebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.SpaceUser;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceRoleEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.SpaceUserService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.yupi.yupicturebackend.model.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component  // 保证此类被SpringBoot扫描，完成 Sa-Token 的自定义权限验证扩展
public class StpInterfaceImpl implements StpInterface {

    // 获取到当前请求类的上下文路径
    @Value("${server.servlet.context-path}")
    private String contextPath;

    // 涉及到用户查询
    @Resource
    private UserService userService;

    // 涉及到空间查询
    @Resource
    private SpaceService spaceService;

    // 涉及到图片查询
    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceUserService spaceUserService;

    // 涉及到权限管理器
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;


    /**
     * 返回一个账号所拥有的权限码集合
     * 【注意】这种是采用的 注解式鉴权 + 通过请求对象获取参数，可能会重复查询数据库！
     * <p>
     * 因此想要更灵活、高效的实现鉴权，还是要使用 编程式鉴权！而获取权限的方法和上下文类都可以复用，
     * 只需要将 getAuthContextByRequest 方法逻辑 改为 从ThreadLocal上下文获取即可。
     *
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1.判断loginType，仅对类型为"space"进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            return new ArrayList<>();
        }

        // 2.设置管理员权限，表示权限校验通过
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionByRole(SpaceRoleEnum.ADMIN.getValue());

        // 3.判断查询字段是否为空：
        // 获取上下文对象，从SpaceUserAuthContext中
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 3.1 如果所有字段都为空，表示查询公共图库，可以通过，调用方法isAllFieldsNull()
        if (isAllFieldsNull(authContext)) {
            return ADMIN_PERMISSIONS; // 直接放行管理员权限
        }

        // 3.2 如果不是全为空，进行权限校验
        // 先获取userId -> 先从Sa-Token的Session中拿到登录用户的Session信息
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        Long userId = loginUser.getId();
        // 3.2.1 再 直接从上下文中拿到SpaceUser对象 (团队成员)
        SpaceUser spaceUser = authContext.getSpaceUser();
        if (spaceUser != null) {
            return spaceUserAuthManager.getPermissionByRole(spaceUser.getSpaceRole());
        }

        // 3.2.2 如果没查到spaceUser，但是有spaceUserId，也一定是团队空间 -> 通过数据库查询SpaceUser对象获取
        Long spaceUserId = authContext.getSpaceUserId(); // 拿到某一个团队成员张三的Id信息
        if (spaceUserId != null) {
            // 查询spaceUser
            spaceUser = spaceUserService.getById(spaceUserId);
            ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");

            // 然后去除当前登录用户对应的 spaceUser -> 要通过spaceId和userId共同确认
            // 查询当前登录用户（我自己）在同一个空间的个人信息
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    // 条件1：跟查到的张三在同一个空间的ID
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    // 当前登录用户（我自己）
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null) {
                return new ArrayList<>();
            }
            // 返回的应该是我自己的权限，而不是张三的权限
            // 因为查的都是空间权限，可能会导致管理员在私有空间无权限，再查一次库处理（这一步没理解，何意味啊？）
            return spaceUserAuthManager.getPermissionByRole(loginSpaceUser.getSpaceRole());
        }

        // 3.2.3 如果没有spaceUserId，尝试通过 spaceId 或 pictureId 获取space对象并处理
        Long spaceId = authContext.getSpaceId();
        // spaceId 为空：
        if (spaceId == null) {
            // 如果没有spaceId，尝试通过Picture 或 pictureId 获取Space对象并处理
            Long pictureId = authContext.getPictureId();
            // 如果pictureId也没有，则说明默认全部权限都通过
            if (pictureId == null) {
                return ADMIN_PERMISSIONS;
            }
            // 有pictureId的话，就通过pictureId -> 再查询对应的spaceId、userId 查到Picture
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            // Picture对象拿到了，就从中获取 spaceId
            spaceId = picture.getSpaceId();
            if (spaceId == null) {
                // 公共图库，仅本人或者管理员可操作
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    return ADMIN_PERMISSIONS;
                } else {
                    // 权限不够，不是自己的图片，就只能查看，不能编辑
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // 如果spaceId不为空，直接查Space对象
        Space space = spaceService.getById(spaceId);
        if (space == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 私有空间，仅本人或管理员有权限，直接放行管理员权限即可
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                return ADMIN_PERMISSIONS;
            } else {
                return new ArrayList<>();
            }
        } else {
            // 非私有，就是团队空间，需要查询SpaceUser并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (spaceUser == null) {
                return new ArrayList<>();
            }
            return spaceUserAuthManager.getPermissionByRole(spaceUser.getSpaceRole());
        }
    }


    /**
     * 本项目中不使用！返回一个账号所拥有的角色标识集合（权限与角色可分开校验）
     *
     * @param loginId
     * @param loginType
     * @return
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }


    /**
     * 从请求中获取上下文对象
     *
     * @return
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 1.先获取请求对象:先拿到Attributes后，再获取Request对象 -> 而且要转换为HttpServletRequest形式！
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        // 2.然后从request中获取当前请求类型,利用Hutool自带的Header方法
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;

        // 3.获取请求参数，并判断
        // 3.1 如果是JSON请求
        if (ContentType.JSON.getValue().equals(contentType)) {
            // 根据Hutool的ServletUtil方法拿到body
            // 【重要 - 开发经验多才看得出来】但是坑爹的是，HttpServletRequest的body值是个流，只支持读取一次，读完就没了！所以还要在config包下自定义请求包装类和请求包装类过滤器（样板代码，拿来直接用即可）。
            String body = ServletUtil.getBody(request);
            // 用JSONUtil把body转为SpaceUserAuthContext
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            // 3.2 如果是Get请求（没有JSON）
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            // 用BeanUtil把paramMap转为SpaceUserAuthContext
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }

        // 4.根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            // 4.1 获取到请求路径的业务前缀，路径目前如：/api/picture/aaa?a=1
            String requestURI = request.getRequestURI();
            // 4.2 先替换掉上下文/api，剩下的就是前缀:/picture/aaa?a=1
            String partURI = requestURI.replace(contextPath + "/", "");
            // 4.3 获取前缀的第一个斜杠前的字符串,如/picture -> 用subBefore(),只要前面，不要最后
            String moduleName = StrUtil.subBefore(partURI, "/", false);
            // 现在已经获取到如:/picture、/spaceUser、/Space的字符串，用switch判断
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        // 5.最后把获取的上下文参数authRequest返回
        return authRequest;
    }


    /**
     * 判断对象的所有字段是否为空
     *
     * @param object
     * @return
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空值
        }

        // 获取所有字段并判断是否所有字段都为空 -> 用反射动态获取对象信息ReflectUtil.getFields()方法
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 遍历，通过反射工具类获取每个字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空：allMatch()必须所有的都符合条件
                .allMatch(ObjectUtil::isEmpty);
    }
}
