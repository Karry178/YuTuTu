package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * 图片分页的请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PictureQueryRequest extends PageRequest implements Serializable {

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

    /**
     * 搜索关键词
     */
    private String searchText;

    /**
     * 用户Id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
