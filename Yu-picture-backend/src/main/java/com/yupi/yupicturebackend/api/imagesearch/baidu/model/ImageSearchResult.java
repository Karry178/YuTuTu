package com.yupi.yupicturebackend.api.imagesearch.baidu.model;

import lombok.Data;

/**
 * 以图搜图结果
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;

    /**
     * 图片标题
     */
    private String title;
}
