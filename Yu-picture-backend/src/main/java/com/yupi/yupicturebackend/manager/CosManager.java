package com.yupi.yupicturebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.yupi.yupicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

/**
 * manager包：主要存放可以服用的代码，不止是与这个项目绑定，而是可以直接复用到其余项目中
 * 直接把manager包复制搬运到其余项目中即可
 * <p>
 * CosManager类：主要实现文件的上传与下载功能
 */
@Component
public class CosManager {

    // 引入CosClient的配置类
    @Resource
    private CosClientConfig cosClientConfig;

    // 引入Cos的客户端
    @Resource
    private COSClient cosClient;


    /**
     * 使用腾讯云-对象存储-参考文档的简单接口代码开发：
     * 将本地文件上传到 COS
     *
     * @param key  唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }


    /**
     * 下载对象
     *
     * @param key 唯一键
     * @return
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }


    /**
     * 补充一个上传并解析图片的方法
     *
     * @param key  唯一键
     * @param file 文件
     * @return
     */
    public PutObjectResult putPictureObject(String key, File file) {
        // 1.上传图片的实现
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 2.对图片进行处理/解析(获取基本信息也被视作一种图片的处理)
        PicOperations picOperations = new PicOperations();
        // 2.1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        // 构造处理参数
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
