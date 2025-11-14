package com.yupi.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片更新请求 - 更新请求一般给管理员用
 */
@Data
public class PictureUpdateRequest implements Serializable {

    /**
     * 图片id（用于修改）
     */
    private Long id;

    /**
     * 图片名称
     */
    private String Name;

    /**
     * 图片描述
     */
    private String introduction;

    /**
     * 图片分类
     */
    private String category;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 标签
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
