package com.yupi.yupicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用的删除请求类 -> 删除都是删Id
 *
 * @author Karry178
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long SerialVersionUID = 1L;
}
