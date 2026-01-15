package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.yupi.yupicturebackend.manager.auth.SpaceUserAuthManager;
import com.yupi.yupicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket的拦截器：建立连接前必须先校验
 * 需要实现接口 HandshakeInterceptor
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    // 引入UserService
    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    // 引入空间用户管理权限校验
    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 建立连接前，需要先校验
     *
     * @param request
     * @param response
     * @param wsHandler
     * @param attributes 给WebSocket的Session会话设置Map属性 key : value
     * @return
     * @throws Exception
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 首先转换为常用的HttpServletRequest接口
        if (request instanceof ServletServerHttpRequest) {
            // 1.获取当前登录用户

            // 如果当前登录的request属于ServletServerHttpRequest，则把request转换为ServletServerHttpRequest，并拿到ServletRequest对象
            // 1.1 这个转换后的ServletRequest就是平时接口中拿到的对象
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 1.2 从请求中获取参数
            String pictureId = httpServletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手！");
                return false;
            }
            // 1.3 pictureId不为空，则获取当前登录用户
            User loginUser = userService.getLoginUser(httpServletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手！");
                return false;
            }

            // 2.校验用户是否有编辑当前图片的权限
            Picture picture = pictureService.getById(pictureId);
            if (ObjUtil.isEmpty(picture)) {
                log.error("图片不存在，拒绝握手！");
                return false;
            }
            // 2.1 拿到对应用户的空间Id与空间
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (ObjUtil.isEmpty(space)) {
                    log.info("空间不存在，拒绝握手！");
                    return false;
                }
                // 2.2 如果空间存在，判断是否为团队空间
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.error("图片所在空间不是团队空间，拒绝握手！");
                    return false;
                }
            }
            // 2.3 拿到空间用户的权限列表
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                // 如果用户的权限列表中不包含 "edit" 编辑权限
                log.error("用户没有编辑图片的权限，拒绝握手！");
            }
            // 2.4 终于可以成功握手了！设置用户登录信息等属性到 WebSocket 会话中
            // 参数attributes是一个Map，插入键值对，相对于给WebSocket会话创建属性
            attributes.put("user", loginUser); // put()方法相对于给attributes设置参数key : value
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureId)); // 请求中拿到的pictureId是String类型，需要转为Long类型
        }
        return true;
    }

    /**
     * 握手后
     *
     * @param request
     * @param response
     * @param wsHandler
     * @param exception
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}
