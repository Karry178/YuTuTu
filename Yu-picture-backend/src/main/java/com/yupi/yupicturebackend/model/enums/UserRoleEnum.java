package com.yupi.yupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户角色枚举
 *
 * @author Karry178
 */
@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }


    /**
     * 通过value获取枚举类 getEnumByValue
     *
     * @param value 枚举类中的value
     * @return 枚举类
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 循环遍历枚举类（小数量可以使用）
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            // 判断输入的value和枚举类中的value是否相等
            if (userRoleEnum.value.equals(value)) {
                return userRoleEnum;
            }
        }

        // ※ 成千上万个枚举类，最好使用Map - 目的是从顺序查找变成直接查找，提升性能
        Map<String, UserRoleEnum> map = new HashMap<>();
        map.put("admin", ADMIN);
        return null;
    }
}
