package com.yupi.yupicturebackend.api.imagesearch.sanliuo.model;

import lombok.Data;

/**
 * 360搜图 以图搜图结果
 */
@Data
public class SoImageSearchResult {

    /**
     * 图片url
     */
    private String imgUrl;

    /**
     * 标题
     */
    private String title;

    /**
     * 图片key
     */
    private String imgKey;

    /**
     * HTTP
     */
    private String http;

    /**
     * HTTPS
     */
    private String https;
}
