package com.yupi.yupicturebackend.manager.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 配置：定义连接
 */
@Configuration // 配置类
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // 引入PictureEditHandler图片消息处理器 这个Bean
    @Resource
    private PictureEditHandler pictureEditHandler;

    // 引入WebSocket的拦截器
    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;


    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册处理器
        registry.addHandler(pictureEditHandler, "/ws/picture/edit")
                // 添加拦截器：WebSocket握手的拦截器
                .addInterceptors(wsHandshakeInterceptor)
                // 设置请求允许的跨域范围
                .setAllowedOrigins("*");
    }
}
