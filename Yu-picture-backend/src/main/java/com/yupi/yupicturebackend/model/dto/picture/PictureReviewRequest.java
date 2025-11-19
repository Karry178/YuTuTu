package com.yupi.yupicturebackend.model.dto.picture;

import com.yupi.yupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片审核的请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PictureReviewRequest extends PageRequest implements Serializable {

    /**
     * 图片id（用于修改）
     */
    private Long id;

    /**
     * 审核状态：0-待审核；1-审核通过；2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;


    private static final long serialVersionUID = 1L;
}
