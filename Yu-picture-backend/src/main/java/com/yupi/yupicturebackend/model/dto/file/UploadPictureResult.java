package com.yupi.yupicturebackend.model.dto.file;

import lombok.Data;

import java.io.Serializable;

/**
 * 上传图片的结果：这个类是 通用文件上传服务的 接收图片解析内容的包装类
 */
@Data
public class UploadPictureResult implements Serializable {

    /**
     * 图片 url地址
     */
    private String url;

    /**
     * 图片的缩略图
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String Name;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    private static final long serialVersionUID = 1L;
}
