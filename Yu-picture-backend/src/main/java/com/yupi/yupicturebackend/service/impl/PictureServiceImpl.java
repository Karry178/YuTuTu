package com.yupi.yupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupicturebackend.api.aliyunai.AliYunAiApi;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.yupi.yupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.yupi.yupicturebackend.exception.BusinessException;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.CosManager;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.*;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.Space;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.service.SpaceService;
import com.yupi.yupicturebackend.service.UserService;
import com.yupi.yupicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 17832
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2025-11-10 22:41:52
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    // 上传图片，需要引用FileManager (已废弃，用下面俩)
    /*@Resource
    private FileManager fileManager;*/

    // 引入UserService
    @Resource
    private UserService userService;

    // 上传图片有两种方法：文件和URL，分别引入
    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    // 引入SpaceService
    @Resource
    private SpaceService spaceService;

    // 引入 编程式事务
    @Resource
    private TransactionTemplate transactionTemplate;

    // 引入阿里云API
    @Resource
    private AliYunAiApi aliYunAiApi;


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
     * @param inputSource        前端传来的上传的图片文件/Url
     * @param pictureUploadRequest 用户上传图片请求体，里面只有id，方便修改图片
     * @param loginUser            登录用户-目的是判断用户有无权限上传图片
     * @return
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1.权限校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR, "用户没登录");
          // 【新增】校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            // 从Service层获取space后检验
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "要操作的空间不存在");
            // 【新增】校验是否有空间的权限，仅空间的管理员可以上传图片！
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 【校验额度】
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }

        // 2.判断是新增还是删除
        Long pictureId = null; // 定义图片id，默认空
        if (pictureUploadRequest != null) {
            // 只有请求上传对象的参数不为空，才可以拿到图片id
            pictureUploadRequest.getId();
        }
        // - 更新的话还要判断图片是否存在(判断图片id不为空，说明图片存在，那就是更新图片的请求了)
        if (pictureId != null) {
            // 根据id获取原始图片
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人和管理员可以编辑图片
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 更新时判断空间id是否一致，避免老图片在空间A，现在新图片更新到了空间B
            // 若没传spaceId，则复用原有图片的spaceId(这样也兼容了公共图库)
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 如果用户传了spaceId，必须和原图片的id一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次的图片空间Id不一致");
                }
            }
        }

        // 3.上传图片，得到图片信息 - 需要引用File/Url的PictureUpload方法 - 上传图片服务
        // 【新增】按照用户id划分目录 -> 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 表示公共图库的内容
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 表示私有空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 根据inputSource的类型区分上传方式
        // 3.1 先默认是文件上传
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        // 3.2 如果inputSource的类型是String，再改成URL上传
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        // 3.3 调用模板方法上传，参数为inputSource
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 按照用户id划分目录
        // 构造要入库的图片信息：设置图片的url和缩略图的url，以及图片的spaceId
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setSpaceId(spaceId);
          // 直接从uploadPictureResult(上传图片的通用包装类)中获取picName，如果单独的图片上传请求体中图片名称不为空，直接拿单独的图片名称替代初始的名称
        String picName = uploadPictureResult.getName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        picture.setPicColor(uploadPictureResult.getPicColor());
        picture.setUserId(loginUser.getId());
        // 或者使用BeanUtils.copyProperties
        // BeanUtils.copyProperties(uploadPictureResult, picture);

        // 4.如果pictureId不为空，说明有图片了，只能进行更新图片操作
        if (pictureId != null) {
            // 如果是更新，需要补充id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 补充审核参数 - 调用fillReviewParams方法
        this.fillReviewParams(picture, loginUser);

        // 5.操作数据库
        // 【更新空间额度】使用编程式事务 => 更新空间的使用额度
            // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            // 只有spaceId不为空(即私有空间)才需要更新额度
            if (finalSpaceId != null) {
                // 插入数据成功后 => 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture; // 用不到返回值，此处随便返回即可
        });

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
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewId = pictureQueryRequest.getReviewId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
          // 【新增】空间id的参数
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        // 【筛选条件】开始、结束编辑时间
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();


        // 3.从多字段中搜索 - searchText需同时从name和introduction中获取
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        // 4.拼接查询条件
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
//        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewId), "reviewId", reviewId);
        // 查询编辑时间(范围搜索 => ge：>=（greater equal）；gt：>（greater than)；lt：< (less than))
        // 前端传入开始、结束编辑时间，用于过滤 editTime 范围
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);

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
        return pictureVO;
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
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        // 3.判断：如果分页列表为空，直接返回分页
        if (CollUtil.isEmpty(pictureList)) {
            // 并获取数据到一个空列表中
            pictureVOPage.setRecords(Collections.emptyList());
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


    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus(); // 新的图片状态
        // 根据拿到的状态枚举值定义一个枚举类
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        // 参数校验：id、状态枚举值非空且不能处于待审核状态(REVIEWING)
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2.判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3.校验审核状态是否重复,即已经是该状态了
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4.数据库操作 -> 更新图片
        Picture updatePicture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);
        // 获取对应的参数
        updatePicture.setReviewId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        // 更新数据库内容
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        // 1.判断当前用户是否为管理员，如果是，自动过审
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建，默认待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }


    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片请求
     * @param loginUser                   登录用户
     * @return 成功创建的图片数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 1.校验参数
        // 获取批量抓取图片请求体中的 搜索词和抓取图片数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
          // 拿到图片名称前缀(如果没有，就给默认值：搜索词searchText)
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多抓取30条数据！");

        // 2.抓取内容
        // 使用正确的 Unsplash 搜索 URL 格式，并 URL 编码搜索词
        String encodedSearchText = java.net.URLEncoder.encode(searchText, java.nio.charset.StandardCharsets.UTF_8);
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync= 1", encodedSearchText);
        Document document;
        // 调用jsoup抓取给定链接中的图片，添加请求头避免被反爬虫拦截
        try {
            document = Jsoup.connect(fetchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();
        } catch (IOException e) {
            log.error("获取页面失败，URL: {}", fetchUrl, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败: " + e.getMessage());
        }
        // 3.解析内容
        // 3.1 尝试多种方式获取图片元素（Unsplash 页面结构可能变化）
        Elements imgElementList = null;
        
        // 方式1：尝试原有的选择器
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNotEmpty(div)) {
            imgElementList = div.select("img.ming");
        }
        
        // 方式2：如果方式1失败，尝试直接查找所有图片
        if (imgElementList == null || imgElementList.isEmpty()) {
            // 尝试查找 Unsplash 搜索结果中的图片（常见的类名）
            imgElementList = document.select("img[src*='unsplash.com'], img[src*='images.unsplash.com']");
        }
        
        // 方式3：如果还是失败，尝试查找所有带有 data-src 或 src 属性的图片
        if (imgElementList == null || imgElementList.isEmpty()) {
            imgElementList = document.select("img[src], img[data-src]");
        }
        
        if (imgElementList == null || imgElementList.isEmpty()) {
            log.error("无法从页面中解析到图片元素，URL: {}, 页面标题: {}", fetchUrl, document.title());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败：无法从页面中找到图片元素，可能是页面结构已变化或需要 JavaScript 渲染");
        }
        
        log.info("成功解析到 {} 个图片元素", imgElementList.size());
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            // 优先使用 data-src（延迟加载），如果没有再使用 src
            String fileUrl = imgElement.attr("data-src");
            if (StrUtil.isBlank(fileUrl)) {
                fileUrl = imgElement.attr("src");
            }
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前图片元素没有有效的 URL，已跳过");
                continue;
            }
            
            // 处理相对 URL，转换为绝对 URL
            if (fileUrl.startsWith("//")) {
                fileUrl = "https:" + fileUrl;
            } else if (fileUrl.startsWith("/")) {
                fileUrl = "https://unsplash.com" + fileUrl;
            }
            
            // 如果正常解析了，就处理图片的地址，防止转义或和对象存储冲突的问题
            // 如：bing.cn?yuyu=dog  应该只保留bing.cn
            // 从fileUrl中获取'?'的下标位置
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            
            // 验证 URL 是否有效（至少包含 http:// 或 https://）
            if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
                log.info("当前链接格式无效，已跳过：{}", fileUrl);
                continue;
            }

            // 4.上传图片(复用 通过url上传图片的方法即可:)
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
              // 构造抓取图片的名称（方便统一上传图片时拿到对应图片名称）
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }

            if (uploadCount >= count ) {
                break;
            }
        }
        return uploadCount;
    }


    /**
     * 清理图片文件
     * @param oldPicture 要清理的图片
     */
    @Override
    @Async
    public void clearPictureFile(Picture oldPicture) {
        // 1.先判断该图片是否被多条记录使用（即判断是否使用了秒传机制）
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 如果查到有不止一条记录用到了该图片，就选择不清理
        if (count > 1) {
            return;
        }
        // 否则，调用CosManager的删除方法
        cosManager.deleteObject(pictureUrl);

        // 再删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }


    /**
     * 删除图片：要进行额度更新，只有私有空间才更新空间额度！
     * @param pictureId
     * @param loginUser
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        // 1.校验
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2.从数据库通过id获取图片 -> 如果图片存在，定义为老图片;不存在就报错
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3.【校验权限，调用checkPictureAuth方法】图片存在了 -> 只有本人和管理员可以删除本张图片
        checkPictureAuth(loginUser, oldPicture);

        // 开启事务
        // 更新空间的使用额度后 => 释放额度
        transactionTemplate.execute(status -> {
            // 4.操作数据库，删除图片
            boolean result = this.removeById(pictureId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            // 4.1 首先必须拿到spaceId，明确一点：只有私有空间才可以更新额度！公共空间增删图片都不更新。
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                // 【健壮性增强】为了避免极端数据导致SQL拼出来的是 ... - null
                ThrowUtils.throwIf(oldPicture.getPicSize() == null, ErrorCode.OPERATION_ERROR, "图片大小为空");
                // 插入数据成功后 => 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount - 1 ")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return true; // 用不到返回值，此处随便返回即可
        });

        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }


    /**
     * 校验空间图片的权限
     * @param loginUser
     * @param picture
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 属于公共图库，仅本人和管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 属于私有空间，仅空间的管理员可以操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }


    /**
     * 编辑图片
     * @param pictureEditRequest
     * @param loginUser
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 1.在此处进行实体类和DTO请求类的转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将list转为String
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());

        // 2.数据校验
        // 调用图片类通用的方法validPicture
        this.validPicture(picture);
        Long id = pictureEditRequest.getId();
        // 从pictureService中通过id查到图片的话，就叫oldPicture
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4.【校验权限】只有本人和管理员可以编辑查到的图片信息
        checkPictureAuth(loginUser, oldPicture);
          // 补充审核参数
        this.fillReviewParams(picture, loginUser);

        // 5.操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 根据颜色搜索个人空间中的图片
     *
     * @param spaceId   图片Id
     * @param picColor  图片颜色
     * @param loginUser 登录用户
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.校验空间权限
        // 2.1 先根据spaceId拿到Space并校验，再判断此空间是否属于登录用户
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有访问权限");
        }
        // 3.查询该空间下的所有图片（图片必须有主色调）  -> 使用lambdaQuery查询：获取图片空间下的spaceId列表，且需要picColor非空的值
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 3.1 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 3.2 如果有图片，则将颜色字符串转换为主色调 (用ColorSimilarUtils中的decode方法 -> 16进制转换)
        Color targetColor = Color.decode(picColor);
        // 4.计算相似度并排序 (用stream流)
        List<Picture> sortedPictureList = pictureList.stream().
                sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor(); // 16进制下的图片主色调
                    // 如果没有主色调，则此图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度, 因为用了归一化，所以越大的数字越相似，但是排序导致越大越往后排序，加一个负号就可以保证大的在前面了。
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 只要前12条
                .collect(Collectors.toList());

        // 5.返回结果 -> 把普通的sortedPictureList转成封装类给前端
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }


    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest
     * @param loginUser
     */
    @Override
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1.获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
          // 校验参数
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2.校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
          // 判断登录用户是否是该空间创建人
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "你没有访问该空间权限");
        }
        // 3.查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4.更新分类和标签（for循环更新）
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        // 5.批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
          // 新建一个方法：将nameRule填充到图片列表中
        fillPictureWithNameRule(pictureList, nameRule);
        // 6.操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }


    /**
     * nameRule 格式：图片{序号}
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        // 1.数据校验
        ThrowUtils.throwIf(StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList), ErrorCode.PARAMS_ERROR);
        // 2.for循环，遍历图片
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                // 给每一个图片做正则表达式的替换
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }


    /**
     * 【阿里云】创建扩图任务
     *
     * @param createPictureOutPaintingTaskRequest
     * @param loginUser
     * @return
     */
    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = this.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 权限校验
        checkPictureAuth(loginUser, picture);

        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());

        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }
}




