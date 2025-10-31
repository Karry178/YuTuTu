package com.yupi.yupicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
// 加入@MapperScan目的是扫描mapper接口
@MapperScan("com.yupi.yupicturebackend.mapper")
// 引入AOP切面后，需添加@EnableAspectJAutoProxy(exposeProxy = true) -> 目的是：启动时，Spring会自动扫描@Aspect注解的类并创建代理对象
@EnableAspectJAutoProxy(exposeProxy = true)
public class YuPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuPictureBackendApplication.class, args);
    }

}
