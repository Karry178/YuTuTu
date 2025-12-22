package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
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

    /**
     * 审核状态：0-待审核；1-审核通过；2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人Id
     */
    private Long reviewId;

    /**
     * 审核时间
     */
    private Date reviewTime;

    /**
     * 【新增】空间id
     */
    private Long spaceId;

    /**
     * 【新增】是否只查询spaceId为 null 的数据
     */
    private boolean nullSpaceId;

    /**
     * 【筛选条件】开始编辑时间
     */
    private Date startEditTime;

    /**
     * 【筛选条件】结束编辑时间
     */
    private Date endEditTime;

    private static final long serialVersionUID = 1L;
}
