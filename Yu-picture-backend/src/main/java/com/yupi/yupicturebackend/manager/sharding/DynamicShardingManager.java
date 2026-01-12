package com.yupi.yupicturebackend.manager.sharding;

import com.baomidou.mybatisplus.extension.toolkit.SqlRunner;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.enums.SpaceTypeEnum;
import com.yupi.yupicturebackend.service.SpaceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.driver.jdbc.core.connection.ShardingSphereConnection;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.mode.manager.ContextManager;
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration;
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration;
import org.apache.shardingsphere.sharding.rule.ShardingRule;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 动态分表管理器 （注释这个Bean，就可以关闭分库分表）
 */
//@Component
@Slf4j
public class DynamicShardingManager {

    // 引入数据库DB
    @Resource
    private DataSource dataSource;

    @Resource
    private SpaceService spaceService;

    private static final String LOGIC_TABLE_NAME = "picture";

    private static final String DATABASE_NAME = "logic_db"; // 配置文件中的数据库名称

    /**
     *
     */
    @PostConstruct // 目的是？
    public void initialize() {
        log.info("初始化动态分表配置...");
        updateShardingTableNodes();
    }

    /**
     * 获取所有动态表名，包括初始表 picture 和 分表 picture_{spaceId}
     *
     * @return
     */
    private Set<String> fetchAllPictureTableNames() {
        // 1.为了测试方便，直接对所有团队空间分表（实际上线改为仅对旗舰版生效）
        // 1.1 首先拿到空间类型为TEAM的spaceId集合
        Set<Long> spaceIds = spaceService.lambdaQuery()
                // 条件1：选择空间类型为TEAM的团队空间
                .eq(Space::getSpaceType, SpaceTypeEnum.TEAM.getValue())
                .list()
                .stream()
                // 根据Id查到对应的space
                .map(Space::getId)
                .collect(Collectors.toSet());

        // 1.2 然后根据拿到的spaceId集合，转换成对应的表名称
        Set<String> tableNames = spaceIds.stream()
                // 转化：对spaceId 转为 LOGIC_TABLE_NAME_spaceId，且LOGIC_TABLE_NAME常量是 picture ==> picture_spaceId
                .map(spaceId -> LOGIC_TABLE_NAME + "_" + spaceId)
                .collect(Collectors.toSet());

        // 2.还要把逻辑表本身添加到tableNames中
        tableNames.add(LOGIC_TABLE_NAME);
        return tableNames;
    }


    /**
     * 【样板代码】首次加载动态分表需要：更新ShardingSphere的 actual-data-nodes 动态表名配置
     */
    private void updateShardingTableNodes() {
        // 1.调用方法，获取表名tableNames
        Set<String> tableNames = fetchAllPictureTableNames();
        // 生成前缀配置
        String newActualDataNodes = tableNames.stream()
                // 把所有的tableName 转换为 yu_picture.tableName
                .map(tableName -> "yu_picture." + tableName)
                .collect(Collectors.joining(","));
        log.info("动态分表 actual-data-nodes 配置：{}", newActualDataNodes);

        // 2.调用方法ContextManager获取ShardingSphere的上下文
        ContextManager contextManager = getContextManager();
        // 然后就是找配置类的内容,即yml文件中ShardingSphere的配置逐级往下找，直到找到rule规则配置
        ShardingSphereRuleMetaData ruleMetaData = contextManager.getMetaDataContexts()
                .getMetaData()
                .getDatabases()
                .get(DATABASE_NAME)
                .getRuleMetaData();

        // 3.根据规则配置找到其中的一条规则
        Optional<ShardingRule> shardingRule = ruleMetaData.findSingleRule(ShardingRule.class);
        if (shardingRule.isPresent()) {
            // 拿到该条规则的配置
            ShardingRuleConfiguration ruleConfig = (ShardingRuleConfiguration) shardingRule.get().getConfiguration();
            // 从该条规则配置中拿到分表配置，需要遍历所有的分表规则
            List<ShardingTableRuleConfiguration> updateRules = ruleConfig.getTables().stream()
                    // 遍历匹配需要的分表规则
                    .map(oldTableRule -> {
                        if (LOGIC_TABLE_NAME.equals(oldTableRule.getLogicTable())) {
                            // 新建一条配置 参数一定是newActualDataNodes，表示新的
                            ShardingTableRuleConfiguration newTableRuleConfig = new ShardingTableRuleConfiguration(LOGIC_TABLE_NAME, newActualDataNodes);
                            // 然后把原来的配置修改为现在的配置 allset，返回
                            newTableRuleConfig.setDatabaseShardingStrategy(oldTableRule.getDatabaseShardingStrategy());
                            newTableRuleConfig.setTableShardingStrategy(oldTableRule.getTableShardingStrategy());
                            newTableRuleConfig.setKeyGenerateStrategy(oldTableRule.getKeyGenerateStrategy());
                            newTableRuleConfig.setAuditStrategy(oldTableRule.getAuditStrategy());
                            return newTableRuleConfig;
                        }
                        return oldTableRule;
                    })
                    .collect(Collectors.toList());

            // 4.把规则设置进表中
            ruleConfig.setTables(updateRules);
            contextManager.alterRuleConfiguration(DATABASE_NAME, Collections.singleton(ruleConfig));
            // 重载数据库
            contextManager.reloadDatabase(DATABASE_NAME);
            log.info("动态分表规则更新成功！");
        } else {
            log.error("未找到ShardingSphere的分片规则配置，动态分表更新失败。");
        }
    }


    /**
     * 获取当前分库分表的上下文
     *
     * @return
     */
    private ContextManager getContextManager() {
        // 从数据库中获取连接并转为ShardingSphere的连接
        try (ShardingSphereConnection connection = dataSource.getConnection().unwrap(ShardingSphereConnection.class)) {
            return connection.getContextManager();
        } catch (SQLException e) {
            throw new RuntimeException("获取 ShardingSphere ContextManager 失败", e);
        }
    }


    /**
     * 动态创建分表：通过拼接sql的方式创建出和picture表结构一样的分表，创建显得分表后记得更新分表节点
     * @param space
     */
    public void createSpacePictureTable(Space space) {
        // 动态创建分表
        // 仅为旗舰版团队空间创建分表
        if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue() && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            // 条件为 先判断空间类型 => TEAM ，然后是空间等级 ==> FLAGSHIP
            // 1.先拿到spaceId
            Long spaceId = space.getId();
            // 2.然后拿到表名：逻辑表名_spaceId
            String tableName = LOGIC_TABLE_NAME + "_" + spaceId;
            // 3.创建新表：动态拼接sql
                // 【易错点】CREATE TABLE后要加空格，LIKE前后都要加空格！
            String createTableSql = "CREATE TABLE " + tableName + " LIKE " + LOGIC_TABLE_NAME;
            try {
                // MyBatis-Plus提供的SqlRunner 动态表执行器
                SqlRunner.db().update(createTableSql);
                // 更新分表
                updateShardingTableNodes();
            } catch (Exception e) {
                log.error("创建图片空间分表失败，空间id = {}", space.getId());
            }
        }
    }
}
