package com.yupi.yupicturebackend.manager.websocket.model;

import com.yupi.yupicturebackend.model.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片编辑响应消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditResponseMessage {

    /**
     * 消息类型：例如"ENTER_EDIT"、"EXIT_EDIT"、"EDIT_ACTION"
     */
    private String type;

    /**
     * 信息
     */
    private String message;

    /**
     * 执行的编辑动作，如：方法、缩小、旋转等
     */
    private String editAction;

    /**
     * 用户信息
     */
    private UserVO user;
}
