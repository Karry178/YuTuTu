package com.yupi.yupicturebackend.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 首页默认显示
 * @author Karry
 */
@Data
public class PictureTagCategory {

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 分类列表
     */
    private List<String> categoryList;
}
