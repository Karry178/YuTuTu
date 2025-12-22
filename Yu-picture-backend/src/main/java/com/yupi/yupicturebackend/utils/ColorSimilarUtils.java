package com.yupi.yupicturebackend.utils;

import java.awt.*;
import java.io.Serializable;

/**
 * 工具类：计算颜色相似度
 * 使用欧氏距离计算两个颜色之间的RGB三色数字距离
 */
public class ColorSimilarUtils implements Serializable {

    private ColorSimilarUtils() {
        // 工具类不需要实例化
    }


    /**
     * 计算两个颜色的相似度
     *
     * @param color1
     * @param color2
     * @return 相似度(0 - 1之间 ， 1为完全一样的颜色)
     */
    public static double calculateSimilarity(Color color1, Color color2) {
        // 颜色1
        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();

        // 颜色2
        int r2 = color1.getRed();
        int g2 = color1.getGreen();
        int b2 = color1.getBlue();

        // 计算两个颜色之间的欧氏距离
        double distance = Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));

        // 计算两个颜色的相似度 (归一化)
        return 1 - distance / Math.sqrt(3 * Math.pow(255, 2));
    }


    /**
     * 根据十六进制颜色代码计算相似度
     *
     * @param hexColor1
     * @param hexColor2
     * @return
     */
    public static double calculateSimilarity(String hexColor1, String hexColor2) {
        Color color1 = Color.decode(hexColor1);
        Color color2 = Color.decode(hexColor2);
        return calculateSimilarity(color1, color2);
    }
}
