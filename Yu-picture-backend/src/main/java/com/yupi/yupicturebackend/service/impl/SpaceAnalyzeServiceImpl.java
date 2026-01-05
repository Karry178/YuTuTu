package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.model.dto.space.analyze.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.space.analyze.*;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.service.SpaceAnalyzeService;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author 17832
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-11-21 11:34:16
 */
@Service
public class SpaceAnalyzeServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceAnalyzeService {

    // 引入UserService
    @Resource
    private UserService userService;

    // 引入SpaceService
    @Resource
    private SpaceService spaceService;

    // 引入PictureService
    @Resource
    private PictureService pictureService;

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 1.参数校验
        // 2.权限校验
        // 2.1 全空间/公共图库，需要从Picture表查询
        if (spaceUsageAnalyzeRequest.isQueryAll() || spaceUsageAnalyzeRequest.isQueryPublic()) {
            // 调用checkSpaceAnalyzeAuth方法
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 统计图库的使用空间
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.select("picSize");
            // 调用fillAnalyzeQueryWrapper方法，对不同的权限查询 -> 目的：自动补充查询范围
            fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, queryWrapper);

            // 然后查图片表 -> 从BaseMapper中调用selectObjs方法，目的是：用返回值为Long的List(Object)替换返回值为List的表
            List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(queryWrapper);
            // 把得到的pictureObjList通过stream映射为Long类型，最后把所有的Long加和组成usedSize
            long usedSize = pictureObjList.stream().mapToLong(obj -> (Long) obj).sum();
            long usedCount = pictureObjList.size(); // 得到列表的count

            // 得到结果后，封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(usedSize);
            spaceUsageAnalyzeResponse.setUsedCount(usedCount);
              // 公共图库/全部空间，无数量和容量限制，也无比例
            spaceUsageAnalyzeResponse.setMaxSize(null);
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setMaxCount(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        } else {
            // 2.2 特定空间需要从Space表查询
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null || spaceId <= 0, ErrorCode.PARAMS_ERROR);
            // 获取空间信息
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验权限
            checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
            // 封装返回结果
            SpaceUsageAnalyzeResponse spaceUsageAnalyzeResponse = new SpaceUsageAnalyzeResponse();
            spaceUsageAnalyzeResponse.setUsedSize(space.getTotalSize());
            spaceUsageAnalyzeResponse.setUsedCount(space.getTotalCount());
            // 私有空间，有数量和容量限制，也无比例
            spaceUsageAnalyzeResponse.setMaxSize(space.getMaxSize());
            spaceUsageAnalyzeResponse.setMaxCount(space.getMaxCount());
              // 计算比例
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            spaceUsageAnalyzeResponse.setSizeUsageRatio(null);
            spaceUsageAnalyzeResponse.setCountUsageRatio(null);
            return spaceUsageAnalyzeResponse;
        }
    }


    /**
     * 获取空间图片分类分析
     *
     * @param spaceCategoryAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);

        // 2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
          // 根据请求参数，补充查询条件，调用fillAnalyzeQueryWrapper
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, queryWrapper);

        // 3.使用MyBatis-Plus的分组查询语法 -> 先查分类，然后是总数、总图片体积，并按category分组
        queryWrapper.select("category", "count(*) as count", "sum(picSize) as totalSize")
                .groupBy("category");

        // 4.执行查询，依旧用BaseMapper，其提供的selectMaps()方法可以查询多个条件映射
        return pictureService.getBaseMapper().selectMaps(queryWrapper)
                .stream()
                .map(result -> {
                    // 把每一条当成一个result，从中取值 -> 取出category字段（转成String格式）、count字段、totalSize字段
                    String category = (String) result.get("category");

                    // Long count = (Long) result.get("count"); 因为result是BigDecimal格式，无法转为Long，要强转为Number格式后再转为Long类型
                    long count = ((Number) result.get("count")).longValue();
                    // Long totalSize = (Long) result.get("totalSize");
                    long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
    }


    /**
     * 获取空间图片标签分析
     * @param spaceTagAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 1.参数校验
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);

        // 2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // 根据请求参数，补充查询条件，调用fillAnalyzeQueryWrapper
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, queryWrapper);

        // 3.查询所有符合条件的标签
        queryWrapper.select("tags");
          // 获取到所有符合条件的标签字符串列表,并转换为String格式的List
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(queryWrapper).stream()
                // 过滤掉无标签的字段 -> 用Hutool的ObjUtil工具
                .filter(ObjUtil::isNotNull)
                // map转换 -> Obj转为String
                .map(Object::toString)
                .collect(Collectors.toList());

        // 4.解析标签并统计
          // 如：["Java", "Python"], ["Java", "PHP"]  ==>  "Java", "Python", "Java", "PHP"
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                // 把数组拆开，用扁平化处理 flatMap函数，把每一条的tagsJson 通过 JSONUtil的toList方法拆开，转化为String
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                // 收集数据后分组，根据tag标签分组求和，求出每个tag的数量
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        // 5.转换为响应对象，按照相应次数排序
          // entrySet()是指tagCountMap的一条记录，即一个key和一个value
        return tagCountMap.entrySet().stream()
                // 排序sorted方法，传入一个比较器：(e1,e2) -> Long.compare(e2的值，e1的值)
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排
                // 转换，把每一个entry转为标签对象，取出key：value
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * 获取空间图片大小分析
     * @param spaceSizeAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {

        // 1.检查权限
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);

        // 2.创建查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
          // 根据请求参数，补充查询条件，调用fillAnalyzeQueryWrapper
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, queryWrapper);

        // 3.查询所有符合条件的图片大小
        queryWrapper.select("picSize");
        List<Long> picSizeList = pictureService.getBaseMapper().selectObjs(queryWrapper)
                .stream()
                .filter(ObjUtil::isNotNull)
                // map转换：把size转为数字类型
                .map(size -> (Long) size)
                .collect(Collectors.toList());

        // 4.定义分段范围，注意使用有序的Map，如LinedHashMap、TreeMap
        LinkedHashMap<String, Long> sizeRanges = new LinkedHashMap<>();
        sizeRanges.put("<100KB", picSizeList.stream().filter(size -> size < 100 * 1024).count());
        sizeRanges.put("100KB-500KB", picSizeList.stream().filter(size -> size >= 100 * 1024 && size < 500 * 1024).count());
        sizeRanges.put("500KB-1MB", picSizeList.stream().filter(size -> size >= 500 * 1024 && size <= 1 * 1024 * 1024).count());
        sizeRanges.put(">1MB", picSizeList.stream().filter(size -> size > 1 * 1024 * 1024).count());

        // 5.转化为响应对象
        return sizeRanges.entrySet().stream()
                // 转换：把key（如<100KB）作为第一个参数，把总数（即最后的count）作为第二个参数value
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }


    /**
     * 获取空间用户上传行为分析
     * @param spaceUserAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {

        // 1.校验权限
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);

        // 2.构造查询条件
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, queryWrapper);
          // 2.1 补充用户 id 查询
        Long userId = spaceUserAnalyzeRequest.getUserId();
          // 只有userId非空时，才能查询
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
          // 2.2 补充分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension(); // 时间维度参数
          // 【sql重要！】使用switch枚举时间维度的参数
        switch(timeDimension) {
            case "day":
                // sql拆分：把时间格式按图片创建时间切分，即年-月-日的格式切分为period,然后取出count值计数
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') as period", "count(*) as count");
                break;
            case "week":
                // YEARWEEK可以自动划分到这一年的某一周
                queryWrapper.select("YEARWEEK(createTime) as period", "count(*) as count");
                break;
            case "month":
                queryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') as period", "count(*) as count");
                break;
            default:
                // ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "不支持的时间维度");
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
          // 2.3 分组排序，按照period分组,且增序排序，时间就是从小到大
        queryWrapper.groupBy("period").orderByAsc("period");

        // 3. 查询并封装结果
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(queryWrapper);
        return queryResult
                .stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    // 下面这个count同样是BigDecimal类型，需要先转为Number，再转为Long类型
                    // Long count = (Long) result.get("count");
                    long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }


    /**
     * 空间使用排行分析 （仅管理员）
     * @param spaceRankAnalyzeRequest
     * @param loginUser
     * @return
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {

        // 1.校验权限 (仅管理员)
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 2.构造查询条件
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                // 一次只输出前N个,limit后必须加空格，因为格式是limit N
                .last("limit " + spaceRankAnalyzeRequest.getTopN());

        // 3.查询并封装结果
        return spaceService.list(queryWrapper);
    }


    /**
     * 校验登录用户的空间权限
     *
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    private void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        // 1.取出请求参数的值
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic(); // 公共图库
        boolean queryAll = spaceAnalyzeRequest.isQueryAll(); // 全部图库(含私有图库)

        // 2.检查权限 - 全空间/公共图库权限分析，仅管理员可以访问
        if (queryAll || queryPublic) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "没有权限访问");
        } else {
            // 分析特定空间，只有特定的用户（即本人或管理员）才可访问
            Long spaceId = spaceAnalyzeRequest.getSpaceId();
            ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
            // 如果校验空间Id -> 空间存在，校验用户权限 -> 调用SpaceService的方法
            spaceService.checkSpaceAuth(loginUser, space);
        }
    }


    /**
     * 根据请求对象封装查询条件 -> 查询对象为图片
     *
     * @param spaceAnalyzeRequest
     * @param queryWrapper
     */
    private void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest, QueryWrapper<Picture> queryWrapper) {
        // 1.获取全部参数
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        boolean queryPublic = spaceAnalyzeRequest.isQueryPublic();
        boolean queryAll = spaceAnalyzeRequest.isQueryAll();
        // 2.鉴权
        // 全空间分析
        if (queryAll) {
            return;
        }
        // 公共图库
        if (queryPublic) {
            queryWrapper.isNull("spaceId"); // 只查询公共图库中spaceId非空的图
            return;
        }
        // 分析特定空间（仅空间创建者和管理员可以编辑）
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId); // 查询spaceId
            return;
        }
        // 三个条件均不满足则抛出异常
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }
}




