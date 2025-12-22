package com.yupi.yupicturebackend.utils;

import java.io.Serializable;

/**
 * 工具类：颜色转换
 */
public class ColorTransformUtils implements Serializable {

    private ColorTransformUtils() {
        // 工具类不需要实例化
    }

    /**
     * 获取标准颜色（将数据万象的5位色值转为6位）
     *
     * @param color
     * @return
     */
    public static String getStandardColor(String color) {
        // 如：0x080e0 => 0x080e00 （每一种rgb色值都有可能只有一个0，要转换为2个0）
        // 如果是6位，不用转换；如果是5位，给第三位后加个0
        if (color.length() == 7) {
            color = "0x" + color.substring(0, 4) + "0" + color.substring(4, 7);
        }
        return color;
    }
}
