package com.yupi.backendinittemplate.controller;

import com.yupi.backendinittemplate.common.BaseResponse;
import com.yupi.backendinittemplate.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// @RestController 表示按照Restful风格返回数据的接口类
@RestController
// @RequestMapping("/") 表示该类下的所有接口，都以/开头
@RequestMapping("/")
public class MainController {

    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }
}
