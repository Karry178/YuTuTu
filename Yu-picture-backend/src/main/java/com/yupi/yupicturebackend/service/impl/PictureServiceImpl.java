package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.FileManager;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author 17832
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-11-10 22:41:52
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    // 上传图片，需要引用FileManager - 上传图片服务
    @Resource
    private FileManager fileManager;

    // 引入UserService
    @Resource
    private UserService userService;


    /**
     * 图片校验
     * @param picture 上传的图
     */
    @Override
    public void validPicture(Picture picture) {
        // 1.先校验picture
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 2.从对象取值 - 拿到图片id、图片url、图片说明
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 检验数据：id非空，url长度在1024以内，介绍字符在800字以内
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "id不能为空");
        // 对url和介绍的字段，可以在用户传入后再校验，不传该字段就不校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "图片介绍过长");
        }
    }

    /**
     * 用户上传图片
     *
     * @param multipartFile        前端传来的上传的图片
     * @param pictureUploadRequest 用户上传图片请求体，里面只有id，方便修改图片
     * @param loginUser            登录用户-目的是判断用户有无权限上传图片
     * @return
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {

        // 1.权限校验
//        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "前端没有传来图片");
//        ThrowUtils.throwIf();
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户没登录");
        // 2.判断是新增还是删除
        Long pictureId = null; // 定义图片id，默认空
        if (pictureUploadRequest != null) {
            // 只有请求上传对象的参数不为空，才可以拿到图片id
            pictureUploadRequest.getId();
        }
        // - 更新的话还要判断图片是否存在(判断图片id不为空，说明图片存在，那就是更新图片的请求了)
        if (pictureId != null) {
            // lambda表达式需要从对象中获取，而非像QueryWrapper直接映射了
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 3.上传图片，得到图片信息 - 需要引用FileManager - 上传图片服务
        // 先给全部图片都加入一个 公有 的参数 - public
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        // 按照用户id划分目录
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setUserId(loginUser.getId());
        // 或者使用BeanUtils.copyProperties
        // BeanUtils.copyProperties(uploadPictureResult, picture);

        // 4.如果pictureId不为空，说明有图片了，只能进行更新图片操作
        if (pictureId != null) {
            // 如果是更新，需要补充id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        // 5.操作数据库
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
        return PictureVO.objToVo(picture);
    }


    /**
     * 获取查询图片对象
     *
     * @param pictureQueryRequest 图片查询请求
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        // 1.新建QueryWrapper
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 2.从对象PictureQueryRequest中获取全部字段
        Long id = pictureQueryRequest.getId();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        Long picSize = pictureQueryRequest.getPicSize();
        List<String> tags = pictureQueryRequest.getTags();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        // 3.从多字段中搜索 - searchText需同时从name和introduction中获取
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
//        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);

        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }


    /**
     * 用户获取图片，只能看到VO的图片（获取单条数据）
     *
     * @param picture
     * @param request
     * @return
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类 - 先调用方法
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息 - 查看是谁传入的这张图片
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            // 调用userService根据Id查用户方法获取用户
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user); // 对查出的用户再设置一次脱敏
            pictureVO.setUser(userVO);
        }
        return null;
    }


    /**
     * 分页获取图片封装列表（获取分页数据）
     *
     * @param picturePage 分页
     * @param request     登录用户
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        // 1.首先取出分页的值，并用列表接收
        List<Picture> pictureList = picturePage.getRecords();
        // 2.新建一个分页对象,并获取全部参数
        Page<PictureVO> pictureVOPage = new Page<>();
        List<PictureVO> records = pictureVOPage.getRecords();
        long total = pictureVOPage.getTotal();
        long size = pictureVOPage.getSize();
        long current = pictureVOPage.getCurrent();

        // 3.判断：如果分页列表为空，直接返回分页
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 4.【下面都是重点】否则，要先把对象列表封装为VO列表 -> stream流,map过滤：每一次都把实体类转VO类
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 5.【重要】关联查询用户信息：通过stream流 -> 先获取图片列表中所有用户id的集合，然后，stream流获取用户id与图片对应
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
          // 从图片获取到的用户ID集合中从数据库查询多条列表，然后给用户id分组放在Map中
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream().collect(Collectors.groupingBy(User::getId));

        // 6.遍历循环封装类拿到数据，填充信息
          // 从pictureVOList中根据userId匹配，找到map中哪一个用户应该填充给该图片
        pictureVOList.forEach(pictureVO -> {
            // 先从图片VO获取用户id
            Long userId = pictureVO.getUserId();
            // 定义user为null
            User user = null;
            // 再判断Map中有无userId的key，有就取出并赋值给user
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 把对应的user转为封装类设置给picture的VO对象 - 意思是通过图片看到上传人的VO信息
            pictureVO.setUser(userService.getUserVO(user));
        });

        // 设置分页值给分页列表
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


}




