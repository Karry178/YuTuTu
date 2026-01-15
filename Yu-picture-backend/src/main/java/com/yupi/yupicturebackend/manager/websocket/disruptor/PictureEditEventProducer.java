package com.yupi.yupicturebackend.manager.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.yupi.yupicturebackend.manager.websocket.model.PictureEditRequestMessage;
import com.yupi.yupicturebackend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件的（生产者）
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    // 引入Disruptor对象
    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        // 1.通过Disruptor拿到ringBuffer
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 2.然后获取可以放置事件的位置
        // 2.1 拿到下一个生成事件的位置
        long next = ringBuffer.next();
        // 2.2 根据下一次的位置获取事件对象 -> 然后赋值 -> 就是把外层传来的参数封装到环形缓冲区得到的事件对象中
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);

        // 3.发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     * 如果队列中的任务没处理完，可以选择 优雅停机
     * 企业上线部署项目一般选择该流程，即让任务在没有任何请求时才关闭
     */
    @PreDestroy
    public void destroy() {
        pictureEditEventDisruptor.shutdown();
    }

}
