package com.yupi.yupicturebackend.manager.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 * picture的分表算法实现类
 */
public class PictureShardingAlgorithm implements StandardShardingAlgorithm<Long> {

    @Override
    public String doSharding(Collection<String> availableTargetNames, PreciseShardingValue<Long> preciseShardingValue) {
        // 实际的分表值 就是 spaceId
        Long spaceId = preciseShardingValue.getValue();
        // 同时拿到逻辑表
        String logicTableName = preciseShardingValue.getLogicTableName();
        // spaceId为null表示 查询所有图片
        if (spaceId == null) {
            return logicTableName; // 直接返回逻辑表，让ShardingSphere查全表
        }
        // 根据spaceId动态生成分表名
        String realTableName = "picture_" + spaceId;
        if (availableTargetNames.contains(realTableName)) {
            // 如果真实的表明在支持的表名里面，就查真实的单张表
            return realTableName;
        } else {
            return logicTableName;
        }
    }


    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames, RangeShardingValue<Long> rangeShardingValue) {
        // 范围查询时（如 spaceId IS NULL 或 spaceId IN (...)）
        // 只返回逻辑表，避免查询所有分表
        String logicTableName = rangeShardingValue.getLogicTableName();
        // 只返回逻辑表，让 ShardingSphere 只查询主表
        List<String> result = new ArrayList<>();
        result.add(logicTableName);
        return result;
    }

    @Override
    public Properties getProps() {
        return null;
    }

    @Override
    public void init(Properties properties) {

    }
}
