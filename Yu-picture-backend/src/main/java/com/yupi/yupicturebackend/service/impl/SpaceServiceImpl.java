package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.model.dto.space.SpaceAddRequest;
import com.yupi.yupicturebackend.model.dto.space.SpaceQueryRequest;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.SpaceLevelEnum;
import com.yupi.yupicturebackend.model.vo.SpaceVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.mapper.SpaceMapper;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.sf.jsqlparser.util.validation.metadata.MetadataContext.exists;

/**
 * @author 17832
 * @description 针对表【space(空间)】的数据库操作Service实现
 * @createDate 2025-11-21 11:34:16
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    // 引入UserService
    @Resource
    private UserService userService;

    // 使用编程式事务，而非声明式事务
    @Resource
    private TransactionTemplate transactionTemplate;



    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    // 打一个声明式事务注解，极端情况下：只有锁释放后才给提交事务，容易造成下一个事务也创建好了，上一个还没提交，造成重复
    // @Transactional
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1.填充参数默认值
          // 先把DTO转为实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
          // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);

        // 2.校验参数
          // 调用validSpace方法
        this.validSpace(space, true);
        // 3.校验权限，非管理员只能创建普通级别的空间
        Long userId = loginUser.getId();
          // 给space填充userId
        space.setUserId(userId);
          // 校验权限
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没权限创建指定级别的空间");
        }

        // 4.控制同一用户只能创建一个私有空间：每个用户一把锁
          // 根据userId生成一个锁，Java8后有一个字符串常量池的概念：相同的字符串有一个固定的相同的存储空间；为了保证锁对象是同一把锁(使用的同样的存储空间)，加上intern()
        String lock = String.valueOf(userId).intern();
        synchronized (lock) {
            // 把业务封装到事务中:使用编程事务
            Long newSpaceId = transactionTemplate.execute(status -> {
                // 判断是否已有空间，使用MyBatis-Plus提供的LambdaQuery
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .exists();
                // 如果已有空间，不能再创建
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户仅有一个私有空间");
                // 否则可以创建
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间失败");
                // 成功的话，返回新写入的id
                return space.getId();
            });
            return newSpaceId;
        }
    }


    /**
     * 校验空间
     *
     * @param space
     * @param add   是否为创建时校验
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);

        // 从空间space对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();

        // 把spaceLevel转为枚举类，更方便校验
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);

        // 判断：
        if (add) {
            // 如果是创建时 校验：spaceName、spaceLevel不能为空值
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
        }
        // 修改数据时，对空间名称和空间级别的 数据校验
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        // 空间级别的数字不存在于枚举类中
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
    }


    /**
     * 获取空间包装类(单条)
     *
     * @param space   空间
     * @param request 登录用户
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 1.调用方法：objToVo 对象转为封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);

        // 2.关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            // 调用userService根据userId获取用户user，再根据查到的user获取脱敏后的用户信息
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            // 最后把查到的userVO设置到spaceVO中，返回spaceVO
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }


    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        // 先获取空间分页的记录，拿到分页列表
        List<Space> spaceList = spacePage.getRecords();
        // 新建一个空间的VO分页对象，传入必要参数
        Page<SpaceVO> spaceVOPage = new Page<>();
        List<SpaceVO> records = spaceVOPage.getRecords();
        long total = spaceVOPage.getTotal();
        long size = spaceVOPage.getSize();
        long current = spaceVOPage.getCurrent();
        // 如果分页列表为空，直接返回分页列表
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        // 对象列表 => 封装对象列表：每一次都把实体类转为VO类，获取spaceVOList
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());

        // 1.关联查询用户信息：通过stream流，先获取空间列表所有用户集合Set，然后stream流获取空间与用户id对应
        Set<Long> userIdSet = spaceList.stream().
                map(Space::getUserId)
                .collect(Collectors.toSet());
        // 从空间列表获取到的用户id集合查询数据库的多条列表，然后给用户id分组存放在Map中
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream().
                collect(Collectors.groupingBy(User::getId));

        // 2.遍历循环封装类拿到数据，填充信息
        // 从spaceVOList中根据userId匹配，找到map中哪个用户应该填充给该空间space
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            // 再判断Map中有无userId的key，有就取出并赋值给user
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 把对应的user转为封装类设置给space的VO对象 - 意思是通过图片看到上传人的VO信息
            spaceVO.setUser(userService.getUserVO(user));
        });

        // 设置分页值给分页列表
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }


    /**
     * 获取查询对象
     *
     * @param spaceQueryRequest 空间查询请求
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }

        // 从空间查询请求 spaceQueryRequest中取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    /**
     * 根据space对象当前的空间级别 给其填充 当前的最大容量和最大条数
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 1.首先，获取到spaceLevel，并转为枚举类
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            // 如果空间级别的枚举不为空值，获取最大容量和最大条数
            long maxSize = spaceLevelEnum.getMaxSize();
              // 只有管理员没给当前空间最大值时，才设定默认最大值
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
              // 最大条数同样的操作
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }
}




