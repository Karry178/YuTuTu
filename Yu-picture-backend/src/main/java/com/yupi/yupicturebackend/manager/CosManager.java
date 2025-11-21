package com.yupi.yupicturebackend.manager;

import cn.hutool.core.io.FileUtil;
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
import java.util.ArrayList;
import java.util.List;

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
     * 删除对象
     * @param key 唯一键
     */
    public void deleteObject(String key) {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
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

        // 3.上传图片时直接压缩图片大小 -> 转成webp格式
          // 先定义一个图片处理规则列表
        List<PicOperations.Rule> rules = new ArrayList<>();
          // 定义转格式后要上传的key
        String webpKey = FileUtil.mainName(key) + ".webp";
          // 构造上传图片的规则
        PicOperations.Rule compressRule = new PicOperations.Rule();
          // 规则要设置3个参数，本身也就3个参数
        compressRule.setFileId(webpKey); // 定义要修改的图片Key
        compressRule.setBucket(cosClientConfig.getBucket()); // 还要设置修改哪个桶中的图片
        compressRule.setRule("imageMogr2/format/webp"); // 定义修改图片格式的规则
          // 在rules中加入当前的压缩规则
        rules.add(compressRule);

        // 再加入缩略图处理规则(仅对文件>20KB的生成缩略图)
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            // 拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "thumbnail." + FileUtil.getSuffix(key);
            // 规则要设置3个参数，本身也就3个参数
            thumbnailRule.setFileId(thumbnailKey); // 定义要修改的图片Key
            thumbnailRule.setBucket(cosClientConfig.getBucket()); // 还要设置修改哪个桶中的图片
            // 缩放规则：/thumbnail/<Width>x<Height> >(大于) 如果大于原图宽带，则不处理
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256)); // 定义修改图片格式的规则
            // 在rules中加入当前的压缩规则
            rules.add(thumbnailRule);
        }

        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
