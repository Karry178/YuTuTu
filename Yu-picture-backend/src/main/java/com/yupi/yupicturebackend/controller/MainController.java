package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController 表示按照Restful风格返回数据的接口类
@RestController
// @RequestMapping("/") 表示该类下的所有接口，都以/开头
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     *
     * @return
     */
    @GetMapping(value = "/health", produces = "application/json")
    public BaseResponse<String> health() {
        // 如果请求过多，但是想一次全给接收下来，并且及时清空线程内容，可以使用异步操作
        new Thread(() -> {
//            Thread.sleep(100000L);
        });

        return ResultUtils.success("ok");
    }
}
