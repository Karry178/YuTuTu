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
import com.yupi.yupicturebackend.manager.FileManager;
import com.yupi.yupicturebackend.manager.upload.FilePictureUpload;
import com.yupi.yupicturebackend.manager.upload.PictureUploadTemplate;
import com.yupi.yupicturebackend.manager.upload.UrlPictureUpload;
import com.yupi.yupicturebackend.model.dto.file.UploadPictureResult;
import com.yupi.yupicturebackend.model.dto.picture.PictureQueryRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureReviewRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.yupi.yupicturebackend.model.dto.picture.PictureUploadRequest;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.PictureReviewStatusEnum;
import com.yupi.yupicturebackend.model.vo.PictureVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import com.yupi.yupicturebackend.service.PictureService;
import com.yupi.yupicturebackend.mapper.PictureMapper;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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
            // 根据id获取原始图片
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            // 仅本人和管理员可以编辑图片
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // lambda表达式需要从对象中获取，而非像QueryWrapper直接映射了
            /*boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");*/
        }

        // 3.上传图片，得到图片信息 - 需要引用File/Url的PictureUpload方法 - 上传图片服务
        // 先给全部图片都加入一个 公有 的参数 - public
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

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
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
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
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewId = pictureQueryRequest.getReviewId();
        Date reviewTime = pictureQueryRequest.getReviewTime();
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
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewId), "reviewId", reviewId);

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
}




