package com.yupi.yupicturebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yupi.yupicturebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditActionEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditResponseMessage;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑 WebSocket 处理器
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    // 每张图片的编辑状态，key：pictureId，value：当前正在编辑的用户Id
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key：pictureId，value：用户会话集合
    // 必须用并发的HashMap -> ConcurrentHashMap，因为后面所有的连接都要用到这些方法，保证线程安全，防止数据丢失，因此用线程安全的HashMap
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    // 引入PictureEditEventProducer 消息生产者
    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;


    /**
     * 连接建立成功
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        // 1.保存会话到集合中
        User user = (User) session.getAttributes().get("user");// 从当前session中获取"user"，拿到当前登录用户
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 1.1 初始化集合，如果首次加入，调用Concurrent的newKeySet方法，把pictureId传到HashMap中
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);
        // 2.构造响应，发送加入编辑的消息通知
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("用户 %s 加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 调用广播方法，广播给所有用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 收到前端发送的消息，根据消息类别处理消息
     *
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        // 获取消息内容，将消息字符串JSON 转换为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);

        // 从Session属性中获取到公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");

        // switch判断:根据消息类型处理信息
        /*switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                // 进入编辑状态
                handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                // 结束编辑状态
                handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                // 编辑状态行为
                handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:
                // 其他消息类型，返回错误提示
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                // 调用session的方法发送，错误提示的消息必须经常JSON转Str后传送
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
                break;
        }*/

        // 根据消息类型处理信息：取消switch，改为 生产消息到 Disruptor 环形队列中
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }


    /**
     * 进入编辑状态
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 限制：一张图同时只允许一人编辑
        // 没有用户正在编辑该图片，才可以进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 没其他人在编辑，就可以编辑，设置用户正在编辑该图
            pictureEditingUsers.put(pictureId, user.getId());
            // 构造响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("用户 %s 开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 广播给所有用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        } else {
            // 如果有人已经在编辑，找到当前编辑者
            Long editingUserId = pictureEditingUsers.get(pictureId);
            // 构造响应
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            // 加入当前编辑者的信息
            if (editingUserId != null) {
                User editingUser = userService.getById(editingUserId);
                pictureEditResponseMessage.setUser(userService.getUserVO(editingUser));
                String message = String.format("用户 %s 正在编辑该图片，请等待对方完成编辑", editingUser.getUserName());
                pictureEditResponseMessage.setMessage(message);
            } else {
                // 图片依旧是编辑状态，兜底
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                pictureEditResponseMessage.setMessage("当前图片正在编辑中，请稍后重试");
            }

            // 把响应发回去
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
        }
    }


    /**
     * 处理编辑操作
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 根据pictureId获取当前正在编辑图片的人的Id
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 获取当前正在编辑的操作
        String editAction = pictureEditRequestMessage.getEditAction();
        // 拿到当前进行的编辑动作枚举类
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            log.error("无效的编辑动作");
            return;
        }
        // 确认是当前的编辑者：editingUserId非空且等于当前用户
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 可以进行编辑操作了 -> 把进行的操作通用响应转发给其他用户即可
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("用户 %s 执行了 %s 操作", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction); // 设置编辑的动作
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 广播给除当前客户端之外的其他用户，避免重复编辑
            // 把session作为排除项，不广播
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }


    /**
     * 退出编辑状态
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws IOException {
        // 获取正在编辑的用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 判断是否为当前编辑者结束编辑状态
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前正在编辑图片的用户
            pictureEditingUsers.remove(pictureId);

            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s 退出了编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 广播给其他用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**
     * 当前端退出连接，要释放资源
     *
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        // 1.先从登录的session属性中获取公共参数
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 2.移除当前登录用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 3.删除会话
        // 3.1 获取当前图片的集合sessionSet
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            // 3.2 只要获取到的集合非空，说明可以移除从当前会话集合中移除当前会话
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                // 3.3 如果sessionSet为空值了，则把pictureId从SessionMap中移除，相对于把最外层的PictureSession的Key也一起移除
                pictureSessions.remove(pictureId);
            }
        }

        // 4.给其余用户返回响应：该用户已离开编辑
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s 离开图片编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 5.把响应广播给全部用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


    /**
     * 定义一个 广播的方法（支持排除掉某个session）
     *
     * @param pictureId
     * @param pictureEditResponseMessage 给客户端发送的图片编辑响应信息（给服务器发Request，给客户端返回Response）
     * @param excludeSession             被排除的session
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
        // 1.先从pictureSessions里拿到pictureId的集合Set
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 如果得到的图片Id集合非空，才可以广播
            // 直接把响应转为String，会产生一个精度丢失的问题！因为该响应中有UserVO属性，UserVO里有userId属性，Long类型经过JSON转化一定会产生精度丢失问题。
            // 1.1 所以要用Jackson库进行转化，创建Jackson库的ObjectMapper -> 是Jackson的核心对象，把Java对象 <-> JSON字符串互相转换（序列化/反序列化）
            ObjectMapper objectMapper = new ObjectMapper();
            // 1.2 配置序列化，将Long类型转换为String
            // SimpleModule是Jackson的插件模块容器，可以放规则
            SimpleModule module = new SimpleModule();
            // 写Long.class和Long.TYPE 目的是 包装类 + 基本类型 都覆盖到
            // 当Jackson遇到包装类型Long时，不按默认数字输出，而是用ToStringSerializer，效果是调用toString()输出JSON值
            module.addSerializer(Long.class, ToStringSerializer.instance);
            // Long.TYPE是基本类型long(即long.class)
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            // 然后把这套规则注册到ObjectMapper上
            objectMapper.registerModule(module);

            // 2.把响应的信息按照textMessage拿到并发送，前提是把响应转成String（用JSON）
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的session不发送
                if (excludeSession != null && session.equals(excludeSession)) {
                    continue;
                }
                // 如果session是打开状态，直接调用sendMessage发消息，参数是textMessage
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }


    /**
     * 广播给该图片的所有用户
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws IOException {
        // 调用广播的方法，但是排除的Session设置为null
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }
}
