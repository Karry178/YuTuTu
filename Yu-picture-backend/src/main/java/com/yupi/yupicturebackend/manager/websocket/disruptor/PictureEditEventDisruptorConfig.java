package com.yupi.yupicturebackend.manager.websocket.disruptor;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 图片编辑事件 Disruptor 配置
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    // 引入PictureEditEventWorkHandler
    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> messageModelRingBuffer() {
        // 1.定义 ringBuffer 的大小
        int bufferSize = 1024 * 256;
        // 2.创建一个新的Disruptor对象，参数分别为：创建事件对象(传递Disruptor的创建工厂)、缓冲区大小、自定义线程工厂
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                // 第一个参数主要是 每次放到缓冲区的数据类型 -> 图片编辑事件的类型
                PictureEditEvent::new,
                bufferSize,
                ThreadFactoryBuilder.create()
                        // 指定创建的线程前缀
                        .setNamePrefix("pictureEditEventDisruptor")
                        .build()
        );
        // 3.绑定消费者：使用worker工作线程执行消费者的事件
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 4.启动 disruptor
        disruptor.start();
        return disruptor;
    }
}
